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

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindPageInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.Pageable
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class PageSpec extends AbstractPredatorSpec {

    void "test page method match"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.MyInterface' , """

import io.micronaut.data.model.entities.Person;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    Page<Person> list(Pageable pageable);
    
    Page<Person> findByName(String title, Pageable pageable);
}
""")

        when: "the list method is retrieved"
        def listMethod = beanDefinition.getRequiredMethod("list", Pageable)
        def listAnn = listMethod.synthesize(PredatorMethod)

        def findMethod = beanDefinition.getRequiredMethod("findByName", String, Pageable)
        def findAnn = findMethod.synthesize(PredatorMethod)

        then:"it is configured correctly"
        listAnn.interceptor() == FindPageInterceptor
        findAnn.interceptor() == FindPageInterceptor
        findMethod.getValue(Query.class, "countQuery", String).get() == 'SELECT COUNT(person) FROM io.micronaut.data.model.entities.Person AS person WHERE (person.name = :p1)'
        findMethod.getValue(PredatorMethod.class, PredatorMethod.META_MEMBER_COUNT_PARAMETERS, AnnotationValue[].class)
                  .get()[0]

    }

    void "test page with @Query that is missing pageable"() {
        when:
        buildRepository('test.MyInterface' , """

import io.micronaut.data.model.entities.Person;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    @Query("from Person p where p.name = :name")
    Page<Person> list(String name);
}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Method must accept an argument that is a Pageable')
    }

    void "test page with @Query that is missing count query"() {
        when:
        buildRepository('test.MyInterface' , """

import io.micronaut.data.model.entities.Person;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    @Query("from Person p where p.name = :name")
    Page<Person> list(String name, Pageable pageable);
}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Query returns a Page and does not specify a \'countQuery\' member')
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
