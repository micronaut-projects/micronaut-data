/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.FindPageInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class QueryAnnotationSpec extends AbstractTypeElementSpec {
    void "test build CRUD repository with no named parameter support"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.*;
import java.util.List;

@Repository
@RepositoryConfiguration(namedParameters = false, implicitQueries = false)
interface MyInterface {

    @Query("from Person p where p.name = :n")
    List<Person> listPeople(String n);   
    
    @Query(value = "select * from person p where p.name like :n",
            countQuery = "select count(*) from person p where p.name like :n")
    io.micronaut.data.model.Page<Person> queryByName(String n, io.micronaut.data.model.Pageable p);
}
""")

        when: "the list method is retrieved"
        def listMethod = beanDefinition.getRequiredMethod("listPeople", String.class)

        then: "It was correctly compiled"
        def ann = listMethod.synthesize(DataMethod)
        ann.rootEntity() == Person
        ann.interceptor() == FindAllInterceptor
        listMethod.getReturnType().type == List

        when: "the findOne method is retrieved"
        def findOne = beanDefinition.getRequiredMethod("queryByName", String.class, Pageable.class)

        then: "It was correctly compiled"
        def ann2 = findOne.synthesize(DataMethod)
        ann2.rootEntity() == Person
        ann2.interceptor() == FindPageInterceptor
    }

    void "test build CRUD repository"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Query;
import java.util.List;

@Repository
interface MyInterface {

    @Query("from Person p where p.name = :n")
    List<Person> listPeople(String n);   
    
    @Query("from Person p where p.name = :n")
    Person queryByName(String n);
    
    @Query("from Person p where p.name = :n")
    Person findPerson(String n);
}
""")

        when: "the list method is retrieved"
        def listMethod = beanDefinition.getRequiredMethod("listPeople", String.class)

        then: "It was correctly compiled"
        def ann = listMethod.synthesize(DataMethod)
        ann.rootEntity() == Person
        ann.interceptor() == FindAllInterceptor
        ann.parameterBinding()[0].name() == 'n'
        ann.parameterBinding()[0].value() == 'n'
        listMethod.getReturnType().type == List

        when: "the findOne method is retrieved"
        def findOne = beanDefinition.getRequiredMethod("queryByName", String.class)

        then: "It was correctly compiled"
        def ann2 = findOne.synthesize(DataMethod)
        ann2.rootEntity() == Person
        ann2.interceptor() == FindOneInterceptor
        ann2.parameterBinding()[0].name() == 'n'
        ann2.parameterBinding()[0].value() == 'n'
        findOne.getReturnType().type == Person
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
