package io.micronaut.data.oracle.graph.r2dbc

import io.micronaut.context.annotation.Property
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.oracle.graph.r2dbc.fans.Fan
import io.micronaut.data.oracle.graph.r2dbc.fans.FanGraph
import io.micronaut.data.oracle.graph.r2dbc.fans.Film
import io.micronaut.data.oracle.graph.r2dbc.fans.FilmGraph
import io.micronaut.data.oracle.graph.r2dbc.fans.Novel
import io.micronaut.data.oracle.graph.r2dbc.fans.NovelGraph
import io.micronaut.data.oracle.graph.r2dbc.invalid.InvalidEdges
import io.micronaut.data.r2dbc.config.R2dbcSchemaContributor
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.runtime.schema.SchemaContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import jakarta.inject.Inject
import reactor.core.publisher.Flux
import spock.lang.Specification

/**
 * Generates the edge tables of a relationship type with several targets, of a relationship declared on both of its
 * nodes, and of an entity in a schema with R2DBC schema generation, then queries them.
 */
@MicronautTest(transactional = false)
@Property(name = "r2dbc.datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "r2dbc.datasources.default.packages", value = "io.micronaut.data.oracle.graph.r2dbc.fans")
class OraclePropertyGraphR2dbcEdgeTablesSpec extends Specification {

    @Inject
    FanGraph fanGraph

    @Inject
    FilmGraph filmGraph

    @Inject
    NovelGraph novelGraph

    @Inject
    ConnectionFactory connectionFactory

    @Inject
    List<R2dbcSchemaContributor> contributors

    @Inject
    RuntimeEntityRegistry entityRegistry

    void setup() {
        sql("DELETE FROM fans_likes_films", "DELETE FROM fans_likes_novels", "DELETE FROM test.novels_sequel",
            "DELETE FROM fans", "DELETE FROM films", "DELETE FROM test.novels")
        fanGraph.save(new Fan(1L, "Ged", [], [])).block()
        fanGraph.save(new Fan(2L, "Tenar", [], [])).block()
        filmGraph.save(new Film(10L, "Tales from Earthsea", [])).block()
        novelGraph.save(new Novel(20L, "Tehanu", [], [])).block()
        novelGraph.save(new Novel(21L, "The Other Wind", [], [])).block()
        sql("INSERT INTO fans_likes_films (fanId, filmsId) VALUES (1, 10)",
            "INSERT INTO fans_likes_novels (fanId, novelsId) VALUES (2, 20)",
            "INSERT INTO test.novels_sequel (novelId, sequelsId) VALUES (20, 21)")
    }

    void "a relationship type with several targets keeps an edge table for each"() {
        expect:
        query("SELECT table_name FROM user_tables").containsAll(["FANS_LIKES_FILMS", "FANS_LIKES_NOVELS"])
        fanGraph.findByFilmsTitle("Tales from Earthsea").map { it.firstName() }.collectList().block() == ["Ged"]
        fanGraph.findByNovelsTitle("Tehanu").map { it.firstName() }.collectList().block() == ["Tenar"]
    }

    void "both sides of a relationship read one edge table"() {
        expect:
        !query("SELECT table_name FROM user_tables").contains("FILMS_LIKES")
        filmGraph.findByFansFirstName("Ged").map { it.title() }.collectList().block() == ["Tales from Earthsea"]

        and: "a node related to its own kind, whichever side is declared first"
        novelGraph.findBySequelsTitle("The Other Wind").map { it.title() }.collectList().block() == ["Tehanu"]
        novelGraph.findByPrequelsTitle("Tehanu").map { it.title() }.collectList().block() == ["The Other Wind"]
    }

    void "a property with a name that is not plain is exposed under that name"() {
        expect:
        fanGraph.findByFirstName("Ged").map { it.id() }.collectList().block() == [1L]
        createStatements(Fan, Film, Novel).any { it.contains('"FIRST NAME" AS "first name"') }
    }

    void "the tables of an entity in a schema are qualified"() {
        given:
        def graph = createStatements(Fan, Film, Novel).find { it.startsWith("CREATE OR REPLACE PROPERTY GRAPH") }

        expect:
        createStatements(Fan, Film, Novel).any { it.startsWith('CREATE TABLE "TEST"."NOVELS_SEQUEL" ') }
        graph.contains('"TEST"."NOVELS" KEY ("ID") LABEL Novel')
        graph.contains('"TEST"."NOVELS_SEQUEL" KEY (')
        graph.contains('REFERENCES "NOVELS" ("ID")')
        contributor(Fan, Film, Novel).buildDropStatements(context(Fan, Film, Novel), persistentEntities(Fan, Film, Novel))
            .containsAll(['DROP TABLE IF EXISTS "TEST"."NOVELS_SEQUEL"', 'DROP TABLE IF EXISTS "TEST"."NOVELS"'])
    }

    void "edge tables that would lose a relationship are rejected"() {
        when:
        createStatements(*entities)

        then:
        def e = thrown(IllegalStateException)
        e.message.contains(message)

        where:
        entities                                                      | message
        [InvalidEdges.Actor, InvalidEdges.Credit, InvalidEdges.Show]  | 'both map to the edge table "CREDIT"'
        [InvalidEdges.Pen, InvalidEdges.Ink]                          | 'both map to the edge table "PENS_LIKES"'
        [InvalidEdges.Reader, InvalidEdges.ReaderLoan, InvalidEdges.Title, InvalidEdges.TitleLoan] | 'are the two sides of one relationship'
    }

    private List<String> createStatements(Class<?>... types) {
        contributor(types).buildCreateStatements(context(types), persistentEntities(types))
    }

    private R2dbcSchemaContributor contributor(Class<?>... types) {
        contributors.find { !it.selectEntities(context(types), persistentEntities(types)).isEmpty() }
    }

    private SchemaContext context(Class<?>... types) {
        new SchemaContext("default", Dialect.ORACLE, null, SchemaGenerate.CREATE_DROP, persistentEntities(types))
    }

    private List<PersistentEntity> persistentEntities(Class<?>... types) {
        types.collect { entityRegistry.getEntity(it) as PersistentEntity }
    }

    private void sql(String... statements) {
        Flux.usingWhen(connectionFactory.create(),
            (Connection connection) -> Flux.fromArray(statements).concatMap { statement ->
                Flux.from(connection.createStatement(statement).execute()).concatMap { result -> result.getRowsUpdated() }
            },
            (Connection connection) -> connection.close()
        ).blockLast()
    }

    private List<String> query(String query) {
        Flux.usingWhen(connectionFactory.create(),
            (Connection connection) -> Flux.from(connection.createStatement(query).execute()).concatMap { result ->
                result.map { row, metadata -> row.get(0, String) }
            },
            (Connection connection) -> connection.close()
        ).collectList().block()
    }
}
