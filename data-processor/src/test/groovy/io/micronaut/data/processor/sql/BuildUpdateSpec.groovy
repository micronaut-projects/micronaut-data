package io.micronaut.data.processor.sql

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.inject.ExecutableMethod

class BuildUpdateSpec extends AbstractDataSpec {

    void "test build insert with datasource set"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect= Dialect.MYSQL)
interface MovieRepository extends CrudRepository<Movie, Integer> {
}

${entity('Movie', [title: String])}
""")
        def method = repository.findPossibleMethods("update")
                .findFirst().get()
        def query = method
                .stringValue(Query)
                .get()
        expect:
        query == 'UPDATE `movie` SET `title`=? WHERE (`id` = ?)'
        method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) ==
                ['title', 'id']

    }
}
