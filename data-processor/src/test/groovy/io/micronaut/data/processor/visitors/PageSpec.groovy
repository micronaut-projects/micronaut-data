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
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindPageInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class PageSpec extends AbstractDataSpec {

    void "test compile error on incorrect property order"() {
        when:
        buildRepository('test.MyInterface' , """

import io.micronaut.data.model.entities.Person;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    Page<Person> findAllByNameNotStartsWith(Pageable pageable, String name);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Unable to implement Repository method: MyInterface.findAllByNameNotStartsWith(Pageable pageable,String name). Parameter [pageable] of method [findAllByNameNotStartsWith] is not compatible with property [name] of entity: io.micronaut.data.model.entities.Person')
    }

    void "test compile error on incorrect property order with multiple items"() {
        when:
        buildRepository('test.MyInterface' , """

import io.micronaut.data.model.entities.Person;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    Page<Person> findByNameOrAge(Pageable pageable, String name, int age);
}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Unable to implement Repository method: MyInterface.findByNameOrAge(Pageable pageable,String name,int age). Parameter [pageable] of method [findByNameOrAge] is not compatible with property [name] of entity')
    }

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

        def alias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))

        when: "the list method is retrieved"
        def listMethod = beanDefinition.getRequiredMethod("list", Pageable)
        def listAnn = listMethod.synthesize(DataMethod)

        def findMethod = beanDefinition.getRequiredMethod("findByName", String, Pageable)
        def findAnn = findMethod.synthesize(DataMethod)


        then:"it is configured correctly"
        listAnn.interceptor() == FindPageInterceptor
        findAnn.interceptor() == FindPageInterceptor
        findMethod.getValue(Query.class, "countQuery", String).get() == "SELECT COUNT($alias) FROM io.micronaut.data.model.entities.Person AS $alias WHERE (${alias}.name = :p1)"
        findMethod.stringValues(DataMethod.class, DataMethod.META_MEMBER_COUNT_PARAMETERS + "Names") == ['p1'] as String[]

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
