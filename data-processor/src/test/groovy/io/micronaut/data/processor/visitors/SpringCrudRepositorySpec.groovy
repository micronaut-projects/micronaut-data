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
import io.micronaut.data.intercept.CountInterceptor
import io.micronaut.data.intercept.DeleteAllInterceptor
import io.micronaut.data.intercept.DeleteOneInterceptor
import io.micronaut.data.intercept.ExistsByInterceptor
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.SaveAllInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class SpringCrudRepositorySpec extends AbstractTypeElementSpec {

    void "test build CRUD repository"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.data.model.entities.Person;
import org.springframework.data.repository.CrudRepository;
import java.util.List;
import io.micronaut.data.annotation.Repository;

@Repository
@Executable
interface MyInterface extends CrudRepository<Person, Long> {

    List<Person> list(String name);

    int count(String name);
}
""")
        def alias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))

        when:"the save method is retrieved"
        def saveMethod = beanDefinition.getRequiredMethod("save", Person.class)

        then:"It was correctly compiled"
        saveMethod.getValue(DataMethod, "entity", String).isPresent()
        saveMethod.getValue(DataMethod, "rootEntity", Class).get() == Person
        saveMethod.getReturnType().type == Person
        saveMethod.getArguments()[0].type == Person

        when:"the save all method is retrieved"
        def saveAll = beanDefinition.getRequiredMethod("saveAll", Iterable.class)

        then:"the save all method was correctly compiled"
        saveAll
        saveAll.getReturnType().asArgument().getFirstTypeVariable().get().type == Person
        saveAll.getArguments()[0].getFirstTypeVariable().get().type == Person
        saveAll.synthesize(DataMethod).rootEntity() == Person
        saveAll.synthesize(DataMethod).interceptor() == SaveAllInterceptor

        when:"the exists by id method is retrieved"
        def existsMethod = beanDefinition.getRequiredMethod("existsById", Long)

        then:"The method is correctly configured"
        existsMethod
        existsMethod.getArguments()[0].type == Long
        existsMethod.synthesize(DataMethod).rootEntity() == Person
        existsMethod.synthesize(DataMethod).interceptor() == ExistsByInterceptor

        when:"the findAll method is retrieved"
        def findAll = beanDefinition.getRequiredMethod("findAll")

        then:"The method is correctly configured"
        findAll
        findAll.getReturnType().asArgument().getFirstTypeVariable().get().type == Person
        findAll.synthesize(DataMethod).rootEntity() == Person
        findAll.synthesize(DataMethod).interceptor() == FindAllInterceptor

        when:"the count method is retrieved"
        def count = beanDefinition.getRequiredMethod("count")

        then:"The method is correctly configured"
        count
        count.getReturnType().type == long.class
        count.synthesize(DataMethod).rootEntity() == Person
        count.synthesize(DataMethod).interceptor() == CountInterceptor

        when:"the list method with named query paremeters is retrieved"
        def listPeople = beanDefinition.getRequiredMethod("list", String)

        then:"The method is correctly configured"
        listPeople
        listPeople.getReturnType().type == List.class
        listPeople.synthesize(DataMethod).rootEntity() == Person
        listPeople.synthesize(DataMethod).interceptor() == FindAllInterceptor

        when:"the count method with named query parameters is retrieved"
        def countPeople = beanDefinition.getRequiredMethod("count", String)

        then:"The method is correctly configured"
        countPeople
        countPeople.getReturnType().type == int.class
        countPeople.synthesize(DataMethod).rootEntity() == Person

        when:"the delete by id method is retrieved"
        def deleteById = beanDefinition.getRequiredMethod("deleteById", Long)

        then:"The method is correctly configured"
        deleteById
        deleteById.getReturnType().type == void .class
        deleteById.synthesize(DataMethod).rootEntity() == Person
        deleteById.synthesize(Query).value() == "DELETE $Person.name  AS ${alias} WHERE (${alias}.id = :p1)"
        deleteById.synthesize(DataMethod).interceptor() == DeleteAllInterceptor

        when:"the deleteAll method is retrieved"
        def deleteAll = beanDefinition.getRequiredMethod("deleteAll")

        then:"The method is configured correctly"
        deleteAll
        deleteAll.getReturnType().type == void .class
        deleteAll.synthesize(DataMethod).rootEntity() == Person
        deleteAll.synthesize(DataMethod).interceptor() == DeleteAllInterceptor

        when:"the deleteOne method is retrieved"
        def deleteOne = beanDefinition.getRequiredMethod("delete", Person)

        then:"The method is configured correctly"
        deleteOne
        deleteOne.getReturnType().type == void .class
        deleteOne.synthesize(DataMethod).rootEntity() == Person
        deleteOne.synthesize(DataMethod).interceptor() == DeleteOneInterceptor

        when:"the deleteAll with ids"
        def deleteIds = beanDefinition.getRequiredMethod("deleteAll", Iterable)

        then:"The method is configured correctly"
        deleteIds
        deleteIds.getReturnType().type == void .class
        deleteIds.synthesize(DataMethod).rootEntity() == Person
        deleteIds.synthesize(DataMethod).interceptor() == DeleteAllInterceptor

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
