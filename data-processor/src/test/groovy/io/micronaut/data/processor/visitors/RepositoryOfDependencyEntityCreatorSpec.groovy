package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.AggregatingTypeElementVisitorProcessor
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.JavaFileObjects
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import org.jspecify.annotations.NonNull
import spock.lang.TempDir

import javax.tools.JavaFileObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * An entity or embeddable compiled into a dependency is never visited as its own
 * element in the compilation that uses it, so the repository that resolves it is
 * the only place its Jackson {@code @JsonCreator} conflict can be reported.
 *
 * <p>Each feature compiles the dependency first, with Core's introspection visitor
 * only, as a library built without Micronaut Data would be, then compiles the
 * repository against those classes.</p>
 */
class RepositoryOfDependencyEntityCreatorSpec extends AbstractDataSpec {

    @TempDir
    Path dependencyClasses

    void "a repository reports the creator conflict of an entity compiled into a dependency"() {
        given:
        compileDependency('legacy.Legacy', '''
package legacy;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public record Legacy(@Id Long id, String name) {
    @JsonCreator
    public Legacy(String value) {
        this(1L, value);
    }
}
''')

        when:
        buildRepository('test.LegacyRepository', '''
@Repository
interface LegacyRepository extends CrudRepository<legacy.Legacy, Long> {
}
''')

        then:
        RuntimeException e = thrown()
        e.message.contains('@JsonCreator Legacy(String value) is the bean introspection creator of [legacy.Legacy]')
    }

    void "a repository reports the creator conflict of an embeddable compiled into a dependency"() {
        given:
        compileDependency('legacy.Address', '''
package legacy;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
public record Address(String city) {
    @JsonCreator
    public static Address parse(String value) {
        return new Address(value);
    }
}
''')

        when:
        buildRepository('test.OwnerRepository', '''
@MappedEntity
record Owner(@Id Long id, @Relation(Relation.Kind.EMBEDDED) legacy.Address address) {
}

@Repository
interface OwnerRepository extends CrudRepository<Owner, Long> {
}
''')

        then:
        RuntimeException e = thrown()
        e.message.contains('@JsonCreator Address.parse(String value) is the bean introspection creator of [legacy.Address]')
    }

    void "a repository accepts an entity compiled into a dependency whose creator conflict falls back to its no-argument constructor"() {
        given:
        compileDependency('legacy.Mutable', '''
package legacy;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;

@MappedEntity
public class Mutable {
    @Id
    private Long id;
    private String name;

    public Mutable() {
    }

    @JsonCreator
    public Mutable(String value) {
        this.name = value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
''')

        when: "the repository compiles; its definition is not inspected because the dependency's classes are only on the compile classpath"
        BeanDefinition<?> repository = buildRepository('test.MutableRepository', '''
@Repository
interface MutableRepository extends CrudRepository<legacy.Mutable, Long> {
}
''')

        then:
        noExceptionThrown()
        repository != null
    }

    @Override
    protected JavaParser newJavaParser() {
        String classpath = System.getProperty('java.class.path') + File.pathSeparator + dependencyClasses
        return new JavaParser() {
            @Override
            protected Set<String> getCompilerOptions() {
                Set<String> options = new LinkedHashSet<>(super.getCompilerOptions())
                options.add('-classpath')
                options.add(classpath)
                return options
            }
        }
    }

    /**
     * Compiles a dependency with Core's introspection visitor only and writes its classes where the
     * repository's compilation finds them on the classpath.
     */
    private void compileDependency(String className, String source) {
        JavaParser parser = new JavaParser() {
            @Override
            protected Set<String> getCompilerOptions() {
                // keep parameter names, as the Micronaut Gradle plugin does for a library
                Set<String> options = new LinkedHashSet<>(super.getCompilerOptions())
                options.add('-parameters')
                return options
            }

            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new TypeElementVisitorProcessor() {
                    @Override
                    protected @NonNull Collection<TypeElementVisitor> findTypeElementVisitors() {
                        return [new IntrospectedTypeElementVisitor()]
                    }
                }
            }

            @Override
            protected AggregatingTypeElementVisitorProcessor getAggregatingTypeElementVisitorProcessor() {
                return new AggregatingTypeElementVisitorProcessor() {
                    @Override
                    protected @NonNull Collection<TypeElementVisitor> findTypeElementVisitors() {
                        return []
                    }
                }
            }
        }
        try {
            for (JavaFileObject output : parser.generate(JavaFileObjects.forSourceString(className, source))) {
                String path = output.toUri().path
                if (path.startsWith('/CLASS_OUTPUT/')) {
                    Path target = dependencyClasses.resolve(path.substring('/CLASS_OUTPUT/'.length()))
                    Files.createDirectories(target.parent)
                    output.openInputStream().withCloseable { Files.copy(it, target) }
                }
            }
        } finally {
            parser.close()
        }
    }
}
