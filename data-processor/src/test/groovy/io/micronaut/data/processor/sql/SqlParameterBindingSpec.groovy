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

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.processor.visitors.AbstractDataSpec

class SqlParameterBindingSpec extends AbstractDataSpec {

    void "test update binding respects data type"() {
        given:
        def repository = buildRepository('test.SaleRepository', """
import io.micronaut.data.tck.entities.Sale;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import java.util.Map;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
interface SaleRepository extends CrudRepository<Sale, Long> {

    void updateData(@Id Long id, Map<String, String> data);
}

""")
        def method = repository.getRequiredMethod("updateData", Long, Map)

        expect:"The repository compiles"
        repository != null
        method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS) == ['JSON', 'LONG'] as String[]
    }

    void "test compile repository"() {
        given:
        def repository = buildRepository('test.ProjectRepository', """
import javax.persistence.Entity;
import javax.persistence.EmbeddedId;
import javax.persistence.Column;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

${TestEntities.compositePrimaryKeyEntities()}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
interface ProjectRepository extends CrudRepository<Project, ProjectId> {
    List<Project> findByNameLikeOrNameNotEqual(String n1, String n2, Pageable pageable);
}
""")
        def method = repository.getRequiredMethod("findByNameLikeOrNameNotEqual", String, String, Pageable)

        expect:"The repository compiles"
        repository != null
        method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS) == ['STRING', 'STRING'] as String[]
        method.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING, int[].class).get() == [0, 1] as int[]
    }

    void "test non-sql compile repository"() {
        given:
        def repository = buildRepository('test.ProjectRepository', """
import javax.persistence.Entity;
import javax.persistence.EmbeddedId;
import javax.persistence.Column;
import io.micronaut.context.annotation.Parameter;
${TestEntities.compositePrimaryKeyEntities()}

@Repository
interface ProjectRepository extends CrudRepository<Project, ProjectId> {
    List<Project> findByNameLikeOrNameNotEqual(String name1, String name2, Pageable pageable);

    void update(@Id ProjectId id, @Parameter("name") String name);
}
""")
        def findMethod = repository.getRequiredMethod("findByNameLikeOrNameNotEqual", String, String, Pageable)

        expect:"The repository compiles"
        repository != null
        findMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS) == [] as String[]
        def names = findMethod.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING + "Names", String[].class)
        names.get() == ["p1", "p2"] as String[]
        def parameterIndex = findMethod.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING, int[].class)
        parameterIndex.get() == [0, 1] as int[]
    }

    void "test compile non-sql repository 2"() {
        given:
        def repository = buildRepository('test.CompanyRepository', """
@Repository
interface CompanyRepository extends io.micronaut.data.tck.repositories.CompanyRepository{
}
""")
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()
        def updatePaths = updateMethod.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING + "Paths", String[].class).get()

        expect:"The repository compiles"
        repository != null
        updatePaths == ['', "lastUpdated", ""] as String[]
    }
}
