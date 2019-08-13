package io.micronaut.data.processor.sql

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.processor.visitors.AbstractDataSpec

class SqlParameterBindingSpec extends AbstractDataSpec {

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
}
