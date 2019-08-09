package io.micronaut.data.processor.sql

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Requires
import spock.lang.Shared

@Requires({ javaVersion <= 1.8 })
class CompositePrimaryKeySpec extends AbstractDataSpec {

    @Shared SourcePersistentEntity entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())

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
interface ProjectRepository extends CrudRepository<Project, ProjectId>{}
""")
        expect:"The repository compiles"
        repository != null
    }

    void "test create table"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE project (department_id INT NOT NULL,project_id_project_id INT NOT NULL,name VARCHAR(255) NOT NULL, PRIMARY KEY(department_id,project_id_project_id));'
    }

    void "test build insert"() {
        given:
        def entity = buildJpaEntity('test.Project', TestEntities.compositePrimaryKeyEntities())
        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildInsert(AnnotationMetadata.EMPTY_METADATA, entity).query

        then:
        sql == 'INSERT INTO project (name,department_id,project_id_project_id) VALUES (?,?,?)'
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
        sql.endsWith('WHERE (project_.department_id = ? AND project_.project_id_project_id = ?)')
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
        sql.startsWith('SELECT project_.department_id, project_.project_id_project_id')

        when:"an id project ins used"
        model = QueryModel.from(entity)
                          .idEq(new QueryParameter("test"))

        model.projections().id()
        sql = builder.buildQuery(model).query

        then:
        sql.startsWith('SELECT project_.department_id, project_.project_id_project_id')
    }
}
