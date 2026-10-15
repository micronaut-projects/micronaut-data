package io.micronaut.data.graph.processor

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQueryParameter
import io.micronaut.data.processor.visitors.MappedEntityVisitor
import io.micronaut.data.processor.visitors.RepositoryTypeElementVisitor
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import org.intellij.lang.annotations.Language

import javax.annotation.processing.SupportedAnnotationTypes

abstract class AbstractGraphDataSpec extends AbstractTypeElementSpec {

    static final String MOVIES = '''
@Node
class Movie {
    @Id
    private String title;
    @MappedProperty("tagline")
    private String description;
    private Integer released;
    @Relationship(type = "ACTED_IN", direction = Direction.INCOMING)
    private List<Role> roles = new ArrayList<>();
    @Relationship(type = "DIRECTED", direction = Direction.INCOMING)
    private List<Person> directors = new ArrayList<>();

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getReleased() { return released; }
    public void setReleased(Integer released) { this.released = released; }
    public List<Role> getRoles() { return roles; }
    public void setRoles(List<Role> roles) { this.roles = roles; }
    public List<Person> getDirectors() { return directors; }
    public void setDirectors(List<Person> directors) { this.directors = directors; }
}

@Node(labels = {"Person", "Actor"})
class Person {
    @Id
    @GeneratedValue
    private String id;
    private String name;
    private Integer born;
    @Relationship(type = "FOLLOWS")
    private Person mentor;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getBorn() { return born; }
    public void setBorn(Integer born) { this.born = born; }
    public Person getMentor() { return mentor; }
    public void setMentor(Person mentor) { this.mentor = mentor; }
}

@RelationshipProperties
class Role {
    @RelationshipId
    private String id;
    private List<String> roles;
    @TargetNode
    private Person person;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public Person getPerson() { return person; }
    public void setPerson(Person person) { this.person = person; }
}

@io.micronaut.core.annotation.Introspected
class MovieSummary {
    private String title;
    private Integer released;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getReleased() { return released; }
    public void setReleased(Integer released) { this.released = released; }
}
'''

    BeanDefinition<?> buildRepository(String name, @Language("JAVA") String source) {
        def pkg = NameUtils.getPackageName(name)
        return buildBeanDefinition(name + BeanDefinitionVisitor.PROXY_SUFFIX, """
package $pkg;

import io.micronaut.data.model.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.*;
import io.micronaut.data.graph.annotation.*;
import io.micronaut.data.graph.processor.TestGraphRepository;
import io.micronaut.data.graph.processor.TestAgeRepository;
import io.micronaut.data.graph.processor.TestOracleRepository;
import java.util.*;

$source
""")
    }

    static String getQuery(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotationMetadata().stringValue(Query).orElseThrow()
    }

    static List<String> getParameterNames(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotationMetadata().getAnnotation(DataMethod)
            .getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
            .collect { it.stringValue(DataMethodQueryParameter.META_MEMBER_NAME).orElse("") }
    }

    static List<String> getParameterProperties(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotationMetadata().getAnnotation(DataMethod)
            .getAnnotations(DataMethod.META_MEMBER_PARAMETERS, DataMethodQueryParameter)
            .collect { p ->
                def path = p.stringValues(DataMethodQueryParameter.META_MEMBER_PROPERTY_PATH)
                path.length > 0 ? path.join('.') : p.stringValue(DataMethodQueryParameter.META_MEMBER_PROPERTY).orElse("")
            }
    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new GraphTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class GraphTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor(), new RepositoryTypeElementVisitor(), new MappedEntityVisitor()]
        }
    }
}
