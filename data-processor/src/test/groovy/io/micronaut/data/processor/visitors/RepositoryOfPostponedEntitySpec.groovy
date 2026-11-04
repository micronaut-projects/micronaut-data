package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.inject.BeanDefinition
import org.jspecify.annotations.NonNull

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement

/**
 * An entity whose static method returns a type that another annotation
 * processor generates in the same compilation cannot be fully read in the round
 * it is first seen. Its repository must still be compiled with a query for
 * every method.
 */
class RepositoryOfPostponedEntitySpec extends AbstractDataSpec {

    void "a repository of a record entity whose static method returns a type generated in the same compilation has its queries"() {
        when:
        BeanDefinition<?> repository = buildRepository('test.WidgetRepository', '''
@MappedEntity
@GenerateBuilder
record Widget(@Id @GeneratedValue Long id, String name) {
    static WidgetBuilder named(String name) {
        return new WidgetBuilder().name(name);
    }
}

@interface GenerateBuilder {}

@Repository
interface WidgetRepository extends CrudRepository<Widget, Long> {
    List<Widget> findByName(String name);
}
''')

        then:
        repository.executableMethods.findAll { it.abstract }*.methodName.containsAll(['findById', 'findByName', 'save'])
        repository.executableMethods.findAll { it.abstract && !it.classValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).present }*.methodName == []
    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected @NonNull List<Processor> getAnnotationProcessors() {
                return [new BuilderGeneratingProcessor()] + super.getAnnotationProcessors()
            }
        }
    }

    /**
     * Generates {@code <Name>Builder} for each type annotated {@code test.GenerateBuilder},
     * as builder generators such as RecordBuilder do.
     */
    static class BuilderGeneratingProcessor extends AbstractProcessor {

        @Override
        Set<String> getSupportedAnnotationTypes() {
            return ['test.GenerateBuilder'] as Set
        }

        @Override
        SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latestSupported()
        }

        @Override
        boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            for (TypeElement annotation : annotations) {
                for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                    String name = element.simpleName.toString()
                    processingEnv.filer.createSourceFile("test.${name}Builder", element).openWriter().withWriter { writer ->
                        writer << """package test;

public class ${name}Builder {
    private String name;

    public ${name}Builder name(String name) {
        this.name = name;
        return this;
    }
}
"""
                    }
                }
            }
            return false
        }
    }
}
