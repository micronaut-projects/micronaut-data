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
package io.micronaut.data.processor.sql

import io.micronaut.data.model.DataType
import io.micronaut.data.model.Pageable
import io.micronaut.data.processor.visitors.AbstractDataSpec

import static io.micronaut.data.processor.visitors.TestUtils.*

class SqlParameterBindingSpec extends AbstractDataSpec {

    void "test custom find all repository return type"() {
        given:
            def repository = buildRepository('test.SaleRepository', """

import io.micronaut.data.annotation.FindInterceptorDef;
import io.micronaut.data.intercept.DataInterceptor;
import io.micronaut.data.tck.entities.Sale;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.util.Map;

@Repository
@RepositoryConfiguration(
        queryBuilder=SqlQueryBuilder.class,
        implicitQueries = false,
        namedParameters = false,
        findInterceptors = {
                @FindInterceptorDef(returnType = MyReturn1.class, isContainer = false, interceptor = MyInterceptor1.class),
                @FindInterceptorDef(returnType = MyReturn2.class, interceptor = MyInterceptor2.class)
        }
)
interface SaleRepository extends CrudRepository<Sale, Long> {

    MyReturn1 findAllById(Long id);

    MyReturn2<Sale> findAllByName(String name);

    MyReturn1 list(Long id);
}

class MyReturn1 {
}

class MyReturn2<E> {
}

abstract class MyInterceptor1 implements DataInterceptor<Object, Object> {
}

abstract class MyInterceptor2 implements DataInterceptor<Object, Object> {
}

""")
            def findAllById = repository.getRequiredMethod("findAllById", Long)
            def findAllByName = repository.getRequiredMethod("findAllByName", String)
            def list = repository.getRequiredMethod("list", Long)

        expect:"The repository compiles"
            repository != null
            getDataTypes(findAllById) == [DataType.LONG]
            getDataInterceptor(findAllById) == "test.MyInterceptor1"
            getDataResultType(findAllById) == "test.MyReturn1"

            getDataTypes(findAllByName) == [DataType.STRING]
            getDataInterceptor(findAllByName) == "test.MyInterceptor2"
            getDataResultType(findAllByName) == "io.micronaut.data.tck.entities.Sale"

            getDataInterceptor(list) == "test.MyInterceptor1"
    }

    void "test update binding respects data type"() {
        given:
        def repository = buildRepository('test.SaleRepository', """
import io.micronaut.data.tck.entities.Sale;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.util.Map;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface SaleRepository extends CrudRepository<Sale, Long> {

    void updateData(@Id Long id, Map<String, String> data);
}

""")
        def method = repository.getRequiredMethod("updateData", Long, Map)

        expect:"The repository compiles"
        repository != null
        getDataTypes(method) == [DataType.JSON, DataType.LONG]
    }

    void "test compile repository"() {
        given:
        def repository = buildRepository('test.ProjectRepository', """
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Column;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

${TestEntities.compositePrimaryKeyEntities()}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface ProjectRepository extends CrudRepository<Project, ProjectId> {
    List<Project> findByNameLikeOrNameNotEqual(String n1, String n2, Pageable pageable);
}
""")
        def method = repository.getRequiredMethod("findByNameLikeOrNameNotEqual", String, String, Pageable)

        expect:"The repository compiles"
        repository != null
        getDataTypes(method) == [DataType.STRING, DataType.STRING, DataType.OBJECT]
        getParameterBindingIndexes(method) == ["0", "1", "2"]
    }

    void "test non-sql compile repository"() {
        given:
        def repository = buildRepository('test.ProjectRepository', """
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Column;
import io.micronaut.context.annotation.Parameter;
${TestEntities.compositePrimaryKeyEntities()}

@Repository
@io.micronaut.context.annotation.Executable
interface ProjectRepository extends CrudRepository<Project, ProjectId> {
    List<Project> findByNameLikeOrNameNotEqual(String name1, String name2, Pageable pageable);

    void update(@Id ProjectId id, @Parameter("name") String name);
}
""")
        def findMethod = repository.getRequiredMethod("findByNameLikeOrNameNotEqual", String, String, Pageable)

        expect:"The repository compiles"
        repository != null
        getDataTypes(findMethod) == []
        getParameterBindingIndexes(findMethod) == ["0", "1"]
        getQueryParameterNames(findMethod) == ["p1", "p2"]
    }

    void "test compile non-sql repository 2"() {
        given:
        def repository = buildRepository('test.CompanyRepository', """
@Repository
@io.micronaut.context.annotation.Executable
interface CompanyRepository extends io.micronaut.data.tck.repositories.CompanyRepository{
}
""")
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()

        expect:"The repository compiles"
        repository != null
        getParameterPropertyPaths(updateMethod) == ["name", "lastUpdated", "myId"]
        getParameterAutoPopulatedProperties(updateMethod) == ["", "lastUpdated", ""]
    }
}
