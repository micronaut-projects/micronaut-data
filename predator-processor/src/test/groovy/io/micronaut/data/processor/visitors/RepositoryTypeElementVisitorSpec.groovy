package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.encoder.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Unroll


class RepositoryTypeElementVisitorSpec extends AbstractTypeElementSpec {

    @Unroll
    void "test JPA find one by dynamic finder #method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
import $returnType.name;

@Repository
interface MyInterface {
    $returnType.simpleName $method(${arguments.entrySet().collect { "$it.value.name $it.key" }.join(',')});    
}


""")
        def parameterTypes = arguments.values() as Class[]

        expect: "The finder is valid"
        !beanDefinition.isAbstract()
        beanDefinition != null

        def executableMethod = beanDefinition.getRequiredMethod(method, parameterTypes)
        executableMethod.getAnnotationMetadata().hasAnnotation(Query)
        executableMethod.getValue(Query, String).get() == query
        executableMethod.getValue(PredatorMethod, "interceptor", Class).get() == interceptor
                validateParameterBinding(executableMethod, arguments)

        where:
        returnType | method                  | arguments        | query                                                                              | interceptor
        Person     | 'findByName'            | [name: String]   | "SELECT DISTINCT person FROM $returnType.name AS person WHERE (person.name = :p1)" | FindOneInterceptor.class
        Person     | 'getByAgeGreaterThan'   | [age: int.class] | "SELECT DISTINCT person FROM $returnType.name AS person WHERE (person.age > :p1)"  | FindOneInterceptor.class
        Person     | 'retrieveByAgeLessThan' | [age: int.class] | "SELECT DISTINCT person FROM $returnType.name AS person WHERE (person.age < :p1)"  | FindOneInterceptor.class
    }

    @Unroll
    void "test JPA find all by dynamic finder #method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
import $returnType.name;
import java.util.List;

@Repository
interface MyInterface {
    List<$returnType.simpleName> $method(${arguments.entrySet().collect { "$it.value.name $it.key" }.join(',')});    
}


""")
        def parameterTypes = arguments.values() as Class[]

        expect: "The finder is valid"
        !beanDefinition.isAbstract()
        beanDefinition != null

        def executableMethod = beanDefinition.getRequiredMethod(method, parameterTypes)
        executableMethod.getAnnotationMetadata().hasAnnotation(Query)
        executableMethod.getValue(Query, String).get() == query
        executableMethod.getValue(PredatorMethod, "interceptor", Class).get() == interceptor
        validateParameterBinding(executableMethod, arguments)

        where:
        returnType | method                  | arguments        | query                                                                              | interceptor
        Person     | 'findAllByName'            | [name: String]   | "SELECT DISTINCT person FROM $returnType.name AS person WHERE (person.name = :p1)" | FindAllInterceptor.class
        Person     | 'getAllByAgeGreaterThan'   | [age: int.class] | "SELECT DISTINCT person FROM $returnType.name AS person WHERE (person.age > :p1)"  | FindAllInterceptor.class
        Person     | 'retrieveAllByAgeLessThan' | [age: int.class] | "SELECT DISTINCT person FROM $returnType.name AS person WHERE (person.age < :p1)"  | FindAllInterceptor.class
    }

    boolean validateParameterBinding(ExecutableMethod method, Map<String, Class<? extends Object>> argumentTypes) {
        def annotations = method.getAnnotation(PredatorMethod).getAnnotations("parameterBinding", Property)
        if (annotations.size() == argumentTypes.size()) {
            return annotations.every() { ann ->
                argumentTypes.containsKey(ann.getValue(String.class).get())
            }
        }
        false
    }
}
