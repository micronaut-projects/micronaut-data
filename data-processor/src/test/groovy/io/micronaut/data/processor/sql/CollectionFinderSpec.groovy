package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class CollectionFinderSpec extends AbstractDataSpec {

    void "test #methodName"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;

@JdbcRepository(dialect = Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Long> {
    $method
}

@MappedEntity
class Movie {
    @Id
    @GeneratedValue
    private Long id;
    private String title;
    @TypeDef(type = DataType.STRING_ARRAY)
    private java.util.List<String> tags;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public java.util.List<String> getTags() { return tags; }
    public void setTags(java.util.List<String> tags) { this.tags = tags; }
}
""")
        def executable = repository.findPossibleMethods(methodName).findFirst().get()

        expect:
        getQuery(executable) == query

        where:
        methodName                   | method                                                                     | query
        'findByTagsIsEmpty'          | 'java.util.List<Movie> findByTagsIsEmpty();'                                | 'SELECT movie_."id",movie_."title",movie_."tags" FROM "movie" movie_ WHERE ((movie_."tags" IS NULL OR cardinality(movie_."tags") = 0))'
        'findByTagsIsNotEmpty'       | 'java.util.List<Movie> findByTagsIsNotEmpty();'                             | 'SELECT movie_."id",movie_."title",movie_."tags" FROM "movie" movie_ WHERE ((movie_."tags" IS NOT NULL AND cardinality(movie_."tags") > 0))'
    }

    void "test IgnoreCase with In is rejected with a clear message"() {
        when:
        buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.POSTGRES)
interface MovieRepository extends CrudRepository<Movie, Long> {
    java.util.List<Movie> findByTitleInIgnoreCase(java.util.List<String> titles);
}

${entity('Movie', [title: String])}
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains("IgnoreCase is not supported with In")
    }

    void "test a property named builtIn is a property, not an In restriction"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect = Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface MovieRepository extends CrudRepository<Movie, Long> {
    java.util.List<Movie> findByBuiltInIgnoreCase(String builtIn);
    java.util.List<Movie> findByBuiltIn(String builtIn);
    java.util.List<Movie> findByBuiltInEquals(String builtIn);
    java.util.List<Movie> findByBuiltInIn(java.util.List<String> builtIns);
    java.util.List<Movie> findByTitleIn(java.util.List<String> titles);
}

${entity('Movie', [title: String, builtIn: String])}
""")

        expect:
        getQuery(repository.findPossibleMethods('findByBuiltInIgnoreCase').findFirst().get()).endsWith('WHERE (LOWER(movie_."built_in") = LOWER(?))')
        getQuery(repository.findPossibleMethods('findByBuiltIn').findFirst().get()).endsWith('WHERE (movie_."built_in" = ?)')
        getQuery(repository.findPossibleMethods('findByBuiltInEquals').findFirst().get()).endsWith('WHERE (movie_."built_in" = ?)')
        getQuery(repository.findPossibleMethods('findByBuiltInIn').findFirst().get()).endsWith('WHERE (movie_."built_in" IN (?))')
        getQuery(repository.findPossibleMethods('findByTitleIn').findFirst().get()).endsWith('WHERE (movie_."title" IN (?))')
    }
}
