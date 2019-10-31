package io.micronaut.data.processor.sql

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.processor.visitors.AbstractDataSpec

class BuildQuerySpec extends AbstractDataSpec {

    void "test build insert with datasource set"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@Repository(value = "secondary")
@JdbcRepository(dialect= Dialect.MYSQL)
interface MovieRepository extends CrudRepository<Movie, Integer> {
    Optional<Movie> findByTitle(String title);
}

${entity('Movie', [title: String])}
""")
        def query = repository.getRequiredMethod("findByTitle", String)
                .stringValue(Query)
                .get()
        expect:
        query == 'SELECT movie_.`id`,movie_.`title` FROM `movie` movie_ WHERE (movie_.`title` = ?)'

    }

    void "test build DTO projection with pageable"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@Repository(value = "secondary")
@JdbcRepository(dialect= Dialect.MYSQL)
interface MovieRepository extends CrudRepository<Movie, Integer> {
    Page<MovieTitle> queryAll(Pageable pageable);
}

${entity('Movie', [title: String])}
${dto('MovieTitle', [title: String])}

""")
        def method = repository.getRequiredMethod("queryAll", Pageable)
        def query = method
                .stringValue(Query)
                .get()


        expect:
        method.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)
        query == 'SELECT movie_.`title` AS title FROM `movie` movie_'

    }

}
