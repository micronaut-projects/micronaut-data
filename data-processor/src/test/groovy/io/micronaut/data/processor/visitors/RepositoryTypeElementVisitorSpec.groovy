/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.SaveEntityInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Shared
import spock.lang.Unroll

import javax.annotation.processing.SupportedAnnotationTypes

import static io.micronaut.data.processor.visitors.TestUtils.getQueryParameterNames

class RepositoryTypeElementVisitorSpec extends AbstractTypeElementSpec {
    @Shared String personAlias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))

    @Unroll
    void "test JPA find one by dynamic finder #method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
import $returnType.name;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface {
    $returnType.simpleName $method(${arguments.entrySet().collect { "$it.value.name $it.key" }.join(',')});    
}


""")
        def parameterTypes = arguments.values() as Class[]

        expect: "The finder is valid"
        !beanDefinition.isAbstract()
        beanDefinition != null

        def executableMethod = beanDefinition.getRequiredMethod(method, parameterTypes)
        executableMethod.getValue(Query, String).orElse(null) == query
        executableMethod.getValue(DataMethod, "interceptor", Class).get() == interceptor
        validateParameterBinding(query, executableMethod, arguments)

        where:
        returnType | method                  | arguments        | query                                                                     | interceptor
        Person     | 'findByName'            | [name: String]   | "SELECT ${personAlias} FROM $returnType.name AS ${personAlias} WHERE (${personAlias}.name = :p1)" | FindOneInterceptor.class
        Person     | 'getByAgeGreaterThan'   | [age: int.class] | "SELECT ${personAlias} FROM $returnType.name AS ${personAlias} WHERE (${personAlias}.age > :p1)"  | FindOneInterceptor.class
        Person     | 'retrieveByAgeLessThan' | [age: int.class] | "SELECT ${personAlias} FROM $returnType.name AS ${personAlias} WHERE (${personAlias}.age < :p1)"  | FindOneInterceptor.class
        Person     | 'savePerson'            | [person: Person] | null                                                                      | SaveEntityInterceptor.class
    }

    @Unroll
    void "test JPA find all by dynamic finder #method"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
import $returnType.name;
import java.util.List;
import io.micronaut.data.model.Pageable;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface {
    List<$returnType.simpleName> $method(${arguments.entrySet().collect { "$it.value.name $it.key" }.join(',')}, Pageable pager);    
}


""")
        def parameterTypes = arguments.values() + Pageable as Class[]

        expect: "The finder is valid"
        !beanDefinition.isAbstract()
        beanDefinition != null

        def executableMethod = beanDefinition.getRequiredMethod(method, parameterTypes)
        executableMethod.getAnnotationMetadata().hasAnnotation(Query)
        executableMethod.getValue(Query, String).get() == query
        executableMethod.getValue(DataMethod, "interceptor", Class).get() == interceptor
        executableMethod.getValue(DataMethod, "pageable", String).get() == 'pager'
        validateParameterBinding(query, executableMethod, arguments)

        where:
        returnType | method                     | arguments        | query                                                                              | interceptor
        Person     | 'findAllByName'            | [name: String]   | "SELECT ${personAlias} FROM $returnType.name AS ${personAlias} WHERE (${personAlias}.name = :p1)" | FindAllInterceptor.class
        Person     | 'getAllByAgeGreaterThan'   | [age: int.class] | "SELECT ${personAlias} FROM $returnType.name AS ${personAlias} WHERE (${personAlias}.age > :p1)"  | FindAllInterceptor.class
        Person     | 'retrieveAllByAgeLessThan' | [age: int.class] | "SELECT ${personAlias} FROM $returnType.name AS ${personAlias} WHERE (${personAlias}.age < :p1)"  | FindAllInterceptor.class
    }

    boolean validateParameterBinding(String query, ExecutableMethod method, Map<String, Class<? extends Object>> argumentTypes) {
        if (query == null) {
            return true
        }
        def names = getQueryParameterNames(method)
        return names.size() == argumentTypes.size()
    }

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor(), new RepositoryTypeElementVisitor()]
        }
    }
}
