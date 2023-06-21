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

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Company
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Unroll

import javax.annotation.processing.SupportedAnnotationTypes
import jakarta.persistence.Entity

class JpaOrderBySpec extends AbstractTypeElementSpec {

    void "test order by method definitions"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Query;
import java.util.List;
import io.micronaut.data.repository.GenericRepository;

import java.net.URL;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Person, Long> {

    List<Person> queryByNameOrderByName(String n);

    List<Person> listOrderByName();

    List<String> listNameOrderByName();

    List<Person> listTop3OrderByName();

    List<Person> findByCompanyUrlOrderByCompanyUrl(URL url);
}
""")
        def alias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))
        def companyAlias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Company))

        when: "the query method is retrieved"
        def findOne = beanDefinition.getRequiredMethod("queryByNameOrderByName", String.class)
        def list = beanDefinition.getRequiredMethod("listOrderByName")
        def listName = beanDefinition.getRequiredMethod("listNameOrderByName")
        def listTop3 = beanDefinition.getRequiredMethod("listTop3OrderByName")
        def findByCompanyUrlOrderByCompanyUrl = beanDefinition.getRequiredMethod("findByCompanyUrlOrderByCompanyUrl", URL.class)

        then: "It was correctly compiled"
        findOne.synthesize(Query).value() == "SELECT ${alias} FROM $Person.name AS ${alias} WHERE (${alias}.name = :p1) ORDER BY ${alias}.name ASC"
        list.synthesize(Query).value() == "SELECT ${alias} FROM $Person.name AS ${alias} ORDER BY ${alias}.name ASC"
        list.synthesize(DataMethod).resultType() == Person
        listName.synthesize(Query).value() == "SELECT ${alias}.name FROM $Person.name AS ${alias} ORDER BY ${alias}.name ASC"
        listName.synthesize(DataMethod).resultType() == String
        listTop3.synthesize(Query).value() == "SELECT ${alias} FROM $Person.name AS ${alias} ORDER BY ${alias}.name ASC"
        listTop3.synthesize(DataMethod).pageSize() == 3
        findByCompanyUrlOrderByCompanyUrl.synthesize(Query).value() == "SELECT ${alias} FROM $Person.name AS ${alias} JOIN ${alias}.company ${alias}${companyAlias} WHERE (${alias}${companyAlias}.url = :p1) ORDER BY ${alias}${companyAlias}.url ASC"
    }

    @Unroll
    void "test order by errors for method #method"() {
        when:
        compileListRepository(
                returnType,
                method,
                arguments
        )

        then: "The finder failed to compile"
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        rootEntity | returnType | method                | arguments | message
        Person     | Person     | 'listOrderByJunk'     | [:]       | 'Cannot order by non-existent property: junk'
        Person     | Person     | 'listJunkOrderByName' | [:]       | 'Cannot project on non-existent property: junk'
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
