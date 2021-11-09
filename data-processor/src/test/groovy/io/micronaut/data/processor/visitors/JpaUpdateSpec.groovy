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
import io.micronaut.data.intercept.UpdateInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Company
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

import static io.micronaut.data.processor.visitors.TestUtils.*

class JpaUpdateSpec extends AbstractTypeElementSpec {

    void "test update by ID"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Query;
import java.util.List;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.repository.GenericRepository;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Person, Long> {

    void update(@Id Long myId, String name);
    
    void updateByName(String nameToUpdate, String name);
}
""")
        def alias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))

        when: "update method is retrieved"
        def updateMethod = beanDefinition.getRequiredMethod("update", Long, String)
        def updateByMethod = beanDefinition.getRequiredMethod("updateByName", String, String)
        def updateAnn = updateMethod.synthesize(DataMethod)
        def updateByAnn = updateByMethod.synthesize(DataMethod)

        then: "It was correctly compiled"
        updateAnn.interceptor() == UpdateInterceptor
        getQuery(updateMethod) == "UPDATE $Person.name ${alias} SET ${alias}.name=:p1 WHERE (${alias}.id = :p2)"
        updateAnn.id() == 'myId'
        getQueryParameterNames(updateMethod) == ['p1', 'p2'] as String[]
        getParameterBindingIndexes(updateMethod) == ['1', '0'] as String[]
        getParameterBindingPaths(updateMethod) == ['', ''] as String[]
        getParameterPropertyPaths(updateMethod) == ['name', 'id'] as String[]
        getParameterAutoPopulatedProperties(updateMethod) == ['', ''] as String[]

        updateByAnn.interceptor() == UpdateInterceptor
        getQuery(updateByMethod) == "UPDATE $Person.name ${alias} SET ${alias}.name=:p1 WHERE (${alias}.name = :p2)"
        getQueryParameterNames(updateByMethod) == ['p1', 'p2'] as String[]
        getParameterBindingIndexes(updateByMethod) == ['1', '0'] as String[]
        getParameterBindingPaths(updateByMethod) == ['', ''] as String[]
        getParameterPropertyPaths(updateByMethod) == ['name', 'name'] as String[]
        getParameterAutoPopulatedProperties(updateByMethod) == ['', ''] as String[]
    }

    void "test update with last updated property"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Company;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Query;
import java.util.List;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.repository.GenericRepository;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Company, Long> {

    void update(@Id Long myId, String name);
    
    void updateByName(String nameToUpdate, String name);
}
""")

        def alias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Company))

        when: "update method is retrieved"
        def updateMethod = beanDefinition.getRequiredMethod("update", Long, String)
        def updateByMethod = beanDefinition.getRequiredMethod("updateByName", String, String)
        def updateAnn = updateMethod.synthesize(DataMethod)
        def updateByAnn = updateByMethod.synthesize(DataMethod)

        then: "It was correctly compiled"
        updateAnn.interceptor() == UpdateInterceptor
        getQuery(updateMethod) == "UPDATE $Company.name ${alias} SET ${alias}.name=:p1,${alias}.lastUpdated=:p2 WHERE (${alias}.myId = :p3)"
        updateAnn.id() == 'myId'
        getQueryParameterNames(updateMethod) == ['p1', 'p2', 'p3'] as String[]
        getParameterBindingIndexes(updateMethod) == ['1', '-1', '0'] as String[]
        getParameterBindingPaths(updateMethod) == ['', '', ''] as String[]
        getParameterPropertyPaths(updateMethod) == ['name', 'lastUpdated', 'myId'] as String[]
        getParameterAutoPopulatedProperties(updateMethod) == ['', 'lastUpdated', ''] as String[]

        updateByAnn.interceptor() == UpdateInterceptor
        getQuery(updateByMethod) == "UPDATE $Company.name ${alias} SET ${alias}.name=:p1,${alias}.lastUpdated=:p2 WHERE (${alias}.name = :p3)"
        getQueryParameterNames(updateByMethod) == ['p1', 'p2', 'p3'] as String[]
        getParameterBindingIndexes(updateMethod) == ['1', '-1', '0'] as String[]
        getParameterBindingPaths(updateMethod) == ['', '', ''] as String[]
        getParameterPropertyPaths(updateMethod) == ['name', 'lastUpdated', 'myId'] as String[]
        getParameterAutoPopulatedProperties(updateMethod) == ['', 'lastUpdated', ''] as String[]
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
