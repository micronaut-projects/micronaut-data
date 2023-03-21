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
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Shared

import static io.micronaut.data.processor.visitors.TestUtils.*

//@IgnoreIf({ !jvm.isJava8() })
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

        expect:"The repository compiles"
        repository != null
        getParameterPropertyPaths(updateMethod) == ["name", "lastUpdated", "myId"] as String[]
        getParameterAutoPopulatedProperties(updateMethod) == ['', "lastUpdated", ""] as String[]
    }

    void "test compile repository"() {
        given:
        def repository = buildRepository('test.ProjectRepository', """
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Column;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.context.annotation.Parameter;
${TestEntities.compositePrimaryKeyEntities()}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class, implicitQueries = false, namedParameters = false)
@io.micronaut.context.annotation.Executable
interface ProjectRepository extends CrudRepository<Project, ProjectId>{
    void update(@Id ProjectId id, @Parameter("name") String name);
    List<Project> findByDepartmentId(Long departmentId);
//    List<Project> findByProjectId(Long projectId);
}
""")
        def findByIdMethod = repository.findPossibleMethods("findById").findFirst().get()
        def updateMethod = repository.findPossibleMethods("update").findFirst().get()
        def findByDepartmentIdMethod = repository.findPossibleMethods("findByDepartmentId").findFirst().get()
//        def findByProjectIdMethod = repository.findPossibleMethods("findByProjectId").findFirst().get()

        expect:"The repository compiles"
        repository != null
        getDataTypes(findByIdMethod) == [DataType.INTEGER, DataType.INTEGER]
        getParameterBindingIndexes(findByIdMethod) == ["0", "0"]
        getParameterPropertyPaths(findByIdMethod) == ["projectId.departmentId", "projectId.projectId"] as String[]
        getParameterBindingPaths(findByIdMethod) == ["departmentId", "projectId"] as String[]

        and:
        getParameterBindingIndexes(updateMethod) == ["1", "0", "0"]
        getParameterPropertyPaths(updateMethod) == ["name", "projectId.departmentId", "projectId.projectId"] as String[]
        getParameterBindingPaths(updateMethod) == ["", "departmentId", "projectId"] as String[]

        and:
        getParameterBindingIndexes(findByDepartmentIdMethod) == ["0"]
        getParameterPropertyPaths(findByDepartmentIdMethod) == ["projectId.departmentId"] as String[]
        getParameterBindingPaths(findByDepartmentIdMethod) == [""] as String[]
    }

    void "test compile repo with composite key relations"() {
        given:
        def repository = buildRepository('test.UserRoleRepository', """
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Column;
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
        def findRoleByUserMethod = repository.findPossibleMethods("findRoleByUser").findFirst().get()

        then:
        getQuery(findRoleByUserMethod) == 'SELECT user_role_id_role_."id",user_role_id_role_."name" FROM "user_role" user_role_ INNER JOIN "role" user_role_id_role_ ON user_role_."id_role_id"=user_role_id_role_."id" WHERE (user_role_."id_user_id" = ?)'
        getParameterBindingIndexes(findRoleByUserMethod) == ["0"]
        getParameterPropertyPaths(findRoleByUserMethod) == ["id.user.id"] as String[]
        getParameterBindingPaths(findRoleByUserMethod) == ["id"] as String[]

        when:
        def deleteByIdMethod = repository.findPossibleMethods("deleteById").findFirst().get()

        then:
        getQuery(deleteByIdMethod) == 'DELETE  FROM "user_role"  WHERE ("id_user_id" = ? AND "id_role_id" = ?)'
        getParameterBindingIndexes(deleteByIdMethod) == ["0", "0"]
        getParameterPropertyPaths(deleteByIdMethod) == ["id.user.id", "id.role.id"] as String[]
        getParameterBindingPaths(deleteByIdMethod) == ["user", "role"] as String[]
    }

    void "test compile repo with composite key relations2"() {
        given:
        def repository = buildRepository('test.EntityWithIdClassRepository', """
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Column;
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder;
import io.micronaut.data.tck.entities.*;
import io.micronaut.data.repository.CrudRepository;

@Repository
@RepositoryConfiguration(queryBuilder=JpaQueryBuilder.class, implicitQueries = true, namedParameters = true)
@io.micronaut.context.annotation.Executable
interface EntityWithIdClassRepository extends CrudRepository<EntityWithIdClass, EntityIdClass> {
    List<EntityWithIdClass> findById1(Long id1);
    List<EntityWithIdClass> findById2(Long id2);
}
""")

        when:
        def findByIdMethod = repository.findPossibleMethods("findById").findFirst().get()
        def findById1Method = repository.findPossibleMethods("findById1").findFirst().get()
        def findById2Method = repository.findPossibleMethods("findById2").findFirst().get()

        then:
        getQuery(findByIdMethod) == 'SELECT entityWithIdClass_ FROM io.micronaut.data.tck.entities.EntityWithIdClass AS entityWithIdClass_ WHERE (entityWithIdClass_.id1 = :p1 AND entityWithIdClass_.id2 = :p2)'
        getParameterBindingIndexes(findByIdMethod) == ["0", "0"]
        getParameterPropertyPaths(findByIdMethod) == ["id1", "id2"] as String[]
        getParameterBindingPaths(findByIdMethod) == ["id1", "id2"] as String[]

        and:
        getParameterBindingIndexes(findById1Method) == ["0"]
        getParameterPropertyPaths(findById1Method) == ["id1"] as String[]
        getParameterBindingPaths(findById1Method) == [""] as String[]

        getParameterBindingIndexes(findById2Method) == ["0"]
        getParameterPropertyPaths(findById2Method) == ["id2"] as String[]
        getParameterBindingPaths(findById2Method) == [""] as String[]
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
