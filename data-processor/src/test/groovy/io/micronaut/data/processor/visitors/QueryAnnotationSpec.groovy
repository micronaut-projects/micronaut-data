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
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.*
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Unroll

import javax.annotation.processing.SupportedAnnotationTypes

import static io.micronaut.data.processor.visitors.TestUtils.getQueryParameterNames

class QueryAnnotationSpec extends AbstractDataSpec {

    void "test native query escaping"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
@Repository
interface MovieRepository extends GenericRepository<Movie, Long> {
  @Query(value = "SELECT '1'::INTEGER", nativeQuery = true)
  @io.micronaut.context.annotation.Executable
  Integer findConstant();
}
${entity('Movie', [title: String, enabled: Boolean])}
""")
        def method = repository.getRequiredMethod("findConstant")
        def query = method
                .stringValue(Query)
                .get()


        expect:
        query == 'SELECT \'1\'::INTEGER'
    }

    void "test named parameter parsing"() {
        given:
        def repository = buildRepository('test.PersonRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface PersonRepository extends GenericRepository<Person, Long> {
  @Query(
    value = "select * from person person_ where person_.name like :n",
    countQuery = "select count(*) from person person_ where person_.name like :n")
  java.util.List<Person> findPeople(String n);
}
""")
        def method = repository.getRequiredMethod("findPeople", String)
        def query = method
                .stringValue(Query, DataMethod.META_MEMBER_RAW_QUERY)
                .get()
        def countQuery = method
                .stringValue(Query, DataMethod.META_MEMBER_RAW_COUNT_QUERY)
                .get()

        expect:
        query == 'select * from person person_ where person_.name like ?'
        countQuery == 'select count(*) from person person_ where person_.name like ?'
    }

    @Unroll
    void "test @Query with update statement #methodName"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@Repository(value = "secondary")
@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends GenericRepository<Movie, Long> {
    @Query("$query")
    void deleteById(Long id);
    
    @Query("$query")
    void updateById(Long id);
    
    @Query(value="WITH task AS (" + 
                "SELECT test_id FROM test where foo != :key LIMIT 1 FOR UPDATE SKIP LOCKED" +
           ")" + 
                "UPDATE test SET foo = :key FROM task WHERE test.test_id = task.test_id" +
                "RETURNING test.*", readOnly=false)
    String nextTask(String key);
}

${entity('Movie', [title: String, enabled: Boolean])}

""")
        def method = repository.getRequiredMethod(methodName, *arguments)
        def queryStr = method
                .stringValue(Query)
                .get()
        def dataMethod = method.synthesize(DataMethod)

        expect:
        queryStr != null
        dataMethod.interceptor() == interceptor

        where:
        methodName   | arguments      | query                                            | interceptor
        'deleteById' | [Long.class]   | 'UPDATE User SET enabled = false WHERE id = :id' | DeleteAllInterceptor
        'updateById' | [Long.class]   | 'UPDATE User SET enabled = false WHERE id = :id' | UpdateInterceptor
        'nextTask'   | [String.class] | 'UPDATE User SET enabled = false WHERE id = :id' | UpdateInterceptor
    }

    void "test @Query with update statement - override"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@Repository(value = "secondary")
@JdbcRepository(dialect= Dialect.MYSQL)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Long> {
    @Query("UPDATE User SET enabled = false WHERE id = :id")
    @Override
    void deleteById(Long id);
}

${entity('Movie', [title: String, enabled: Boolean])}

""")
        def method = repository.getRequiredMethod("deleteById", Long)
        def query = method
                .stringValue(Query)
                .get()


        expect:
        query == 'UPDATE User SET enabled = false WHERE id = :id'
    }

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
@io.micronaut.context.annotation.Executable
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
@io.micronaut.context.annotation.Executable
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
        getQueryParameterNames(listMethod) == ['n'] as String[]
        listMethod.getReturnType().type == List

        when: "the findOne method is retrieved"
        def findOne = beanDefinition.getRequiredMethod("queryByName", String.class)

        then: "It was correctly compiled"
        def ann2 = findOne.synthesize(DataMethod)
        ann2.rootEntity() == Person
        ann2.interceptor() == FindOneInterceptor
        getQueryParameterNames(listMethod) == ['n'] as String[]
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
