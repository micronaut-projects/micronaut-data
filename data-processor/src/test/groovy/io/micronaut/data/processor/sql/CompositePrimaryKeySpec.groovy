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

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.User
import io.micronaut.inject.ExecutableMethod
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared

@IgnoreIf({ !jvm.isJava8() })
class CompositePrimaryKeySpec extends AbstractDataSpec {

    @Shared SourcePersistentEntity entity

    def setupSpec() {
        entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
    }

    void "test compile repository 2"() {
        given:
        def repository = buildRepository('test.CompanyRepository', """
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface CompanyRepository extends io.micronaut.data.tck.repositories.CompanyRepository {
}
""")
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()
        def updatePaths = updateMethod.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING + "Paths", String[].class).get()

        expect:"The repository compiles"
        repository != null
        updatePaths == ['', "lastUpdated", ""] as String[]
    }

    void "test compile repository"() {
        given:
        def repository = buildRepository('test.ProjectRepository', """
import javax.persistence.Entity;
import javax.persistence.EmbeddedId;
import javax.persistence.Column;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.context.annotation.Parameter;
${TestEntities.compositePrimaryKeyEntities()}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface ProjectRepository extends CrudRepository<Project, ProjectId>{
    void update(@Id ProjectId id, @Parameter("name") String name);
}
""")
        def findByIdMethod = repository.findPossibleMethods("findById").findFirst().get()
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()
        def paths = findByIdMethod.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING + "Paths", String[].class).get()
        def updatePaths = updateMethod.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING + "Paths", String[].class).get()

        expect:"The repository compiles"
        repository != null
        findByIdMethod.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_TYPE_DEFS) == ['INTEGER', 'INTEGER'] as String[]
        findByIdMethod.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING, int[].class).get() == [-1,-1] as int[]

        and:"include paths for embedded parameters"
        paths == ["0.departmentId", "0.projectId"] as String[]

        and:"Don't include paths for regular parameters that are not the embedded id"
        updatePaths == ['', "0.departmentId", "0.projectId"] as String[]
    }

    void "test compile repo with composite key relations"() {
        given:
        def repository = buildRepository('test.UserRoleRepository', """
import javax.persistence.Entity;
import javax.persistence.EmbeddedId;
import javax.persistence.Column;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.context.annotation.Parameter;
${TestEntities.compositeRelationPrimaryKeyEntities()}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface UserRoleRepository extends GenericRepository<UserRole, UserRoleId> {
        
    UserRole save(UserRole entity);

    default UserRole save(User user, Role role) {
        return save(new UserRole(new UserRoleId(user, role)));
    }

    void deleteById(UserRoleId id);

    default void delete(User user, Role role) {
        deleteById(new UserRoleId(user, role));
    }

    int count();

    Iterable<Role> findRoleByUser(User user);
}
""")

        when:
        def method = repository.findPossibleMethods(methodName).findFirst().get()

        then:
        method.stringValue(Query).get() == query

        where:
        methodName               | query
        'findRoleByUser'         | 'SELECT user_role_id_role_."id",user_role_id_role_."name" FROM "user_role" user_role_ INNER JOIN "role" user_role_id_role_ ON user_role_."id_role_id"=user_role_id_role_."id" WHERE (user_role_."id_user_id" = ?)'
    }

    void "test create table"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE "project" ("department_id" INT NOT NULL,"project_id_project_id" INT AUTO_INCREMENT,"name" VARCHAR(255) NOT NULL, PRIMARY KEY("department_id","project_id_project_id"));'
    }

    void "test build insert"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity).query

        then:
        sql == 'INSERT INTO "project" ("name","department_id") VALUES (?,?)'
    }

    void "test build query"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        def model = QueryModel.from(entity)
                .idEq(new QueryParameter("test"))

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildQuery(model).query

        then:
        sql == 'SELECT project_."department_id",project_."project_id_project_id",project_."name" FROM "project" project_ WHERE (project_."department_id" = ? AND project_."project_id_project_id" = ?)'
    }

    void "test build query projection"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        QueryModel model = QueryModel.from(entity)
                .idEq(new QueryParameter("test"))

        model.projections().property(entity.identity.name)

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildQuery(model).query

        then:
        sql.startsWith('SELECT project_."department_id",project_."project_id_project_id"')

        when:"an id project ins used"
        model = QueryModel.from(entity)
                          .idEq(new QueryParameter("test"))

        model.projections().id()
        sql = builder.buildQuery(model).query

        then:
        sql.startsWith('SELECT project_."department_id",project_."project_id_project_id"')
    }
}
