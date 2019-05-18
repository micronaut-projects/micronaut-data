/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Unroll

import javax.annotation.processing.SupportedAnnotationTypes
import javax.persistence.Entity

class JpaProjectionsSpec extends AbstractTypeElementSpec {


    @Unroll
    void "test JPA single result projection finder for method #method"() {
        given:
        BeanDefinition beanDefinition = compileRepository(returnType, method, arguments)
        def parameterTypes = arguments.values() as Class[]

        expect: "The finder is valid"
        !beanDefinition.isAbstract()
        beanDefinition != null

        def executableMethod = beanDefinition.getRequiredMethod(method, parameterTypes)
        def ann = executableMethod.synthesize(PredatorMethod)
        ann.interceptor() == interceptor
        ann.rootEntity() == rootEntity
        ann.resultType() == returnType
        executableMethod.getValue(PredatorMethod, "interceptor", Class).get() == interceptor
        executableMethod.getValue(Query, String).orElse(null) == query

        where:
        rootEntity | returnType | method                   | arguments      | query                                                                                    | interceptor
        Person     | Person     | 'findDistinctByName'     | [name: String] | "SELECT DISTINCT(person) FROM $rootEntity.name AS person WHERE (person.name = :p1)"      | FindOneInterceptor
        Person     | int.class  | 'findAgeByName'          | [name: String] | "SELECT person.age FROM $rootEntity.name AS person WHERE (person.name = :p1)"            | FindOneInterceptor
        Person     | String     | 'findDistinctNameByName' | [name: String] | "SELECT DISTINCT(person.name) FROM $rootEntity.name AS person WHERE (person.name = :p1)" | FindOneInterceptor.class
    }

    @Unroll
    void "test JPA iterable result projection finder for method #method"() {
        given:
        BeanDefinition beanDefinition = compileListRepository(resultType, method, arguments)
        def parameterTypes = arguments.values() as Class[]

        expect: "The finder is valid"
        !beanDefinition.isAbstract()
        beanDefinition != null

        def executableMethod = beanDefinition.getRequiredMethod(method, parameterTypes)
        def ann = executableMethod.synthesize(PredatorMethod)
        ann.interceptor() == interceptor
        ann.rootEntity() == rootEntity
        ann.resultType() == resultType
        executableMethod.getValue(PredatorMethod, "interceptor", Class).get() == interceptor
        executableMethod.getValue(Query, String).orElse(null) == query

        where:
        rootEntity | resultType    | method                   | arguments      | query                                                                                    | interceptor
        Person     | Person        | 'findDistinctByName'     | [name: String] | "SELECT DISTINCT(person) FROM $rootEntity.name AS person WHERE (person.name = :p1)"      | FindAllInterceptor
        Person     | Integer.class | 'findAgeByName'          | [name: String] | "SELECT person.age FROM $rootEntity.name AS person WHERE (person.name = :p1)"            | FindAllInterceptor
        Person     | String        | 'findDistinctNameByName' | [name: String] | "SELECT DISTINCT(person.name) FROM $rootEntity.name AS person WHERE (person.name = :p1)" | FindAllInterceptor.class
    }

    @Unroll
    void "test JPA projection finder for method #method - compile errors"() {
        when:
        compileRepository(
                returnType,
                method,
                arguments
        )

        then: "The finder failed to compile"
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        rootEntity | returnType | method                  | arguments         | message
        Person     | String     | 'findAgeByName'         | [name: String]    | 'Query results in a type [int] whilst method returns an incompatible type: java.lang.String'
        Person     | String     | 'findDistinctAgeByName' | [name: String]    | 'Query results in a type [int] whilst method returns an incompatible type: java.lang.String'
        Person     | int.class  | 'findNameByAge'         | [name: int.class] | 'Query results in a type [java.lang.String] whilst method returns an incompatible type: int'
    }

    @CompileStatic
    BeanDefinition compileRepository(Class returnType, String method, Map<String, Class> arguments) {
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
${returnType.isAnnotationPresent(Entity) ? 'import ' + returnType.getName() + ';' : ''}
import io.micronaut.data.model.entities.Person;
import io.micronaut.data.repository.GenericRepository;

@Repository
interface MyInterface extends GenericRepository<Person, Long>{
    $returnType.simpleName $method(${arguments.entrySet().collect { "$it.value.name $it.key" }.join(',')});    
}


""")
        return beanDefinition
    }

    @CompileStatic
    BeanDefinition compileListRepository(Class returnType, String method, Map<String, Class> arguments) {
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
${returnType.isAnnotationPresent(Entity) ? 'import ' + returnType.getName() + ';' : ''}
import io.micronaut.data.model.entities.Person;
import java.util.List;
import io.micronaut.data.repository.GenericRepository;

@Repository
interface MyInterface extends GenericRepository<Person, Long>{
    List<$returnType.simpleName> $method(${arguments.entrySet().collect { "$it.value.name $it.key" }.join(',')});    
}


""")
        return beanDefinition
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
