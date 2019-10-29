package io.micronaut.data.processor.sql

import io.micronaut.data.annotation.Query
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

}
