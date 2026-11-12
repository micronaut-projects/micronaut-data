package io.micronaut.data.oracle.graph

import io.micronaut.context.annotation.Property
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.jdbc.config.JdbcSchemaContributor
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.oracle.graph.fans.Fan
import io.micronaut.data.oracle.graph.fans.FanGraph
import io.micronaut.data.oracle.graph.fans.Film
import io.micronaut.data.oracle.graph.fans.FilmGraph
import io.micronaut.data.oracle.graph.fans.Novel
import io.micronaut.data.oracle.graph.fans.NovelGraph
import io.micronaut.data.oracle.graph.invalid.InvalidEdges
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.ResultSet

/**
 * Generates the edge tables of a relationship type with several targets, of a relationship declared on both of its
 * nodes, and of an entity in a schema, then queries them.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.packages", value = "io.micronaut.data.oracle.graph.fans")
class OraclePropertyGraphEdgeTablesSpec extends Specification {

    @Inject
    FanGraph fanGraph

    @Inject
    FilmGraph filmGraph

    @Inject
    NovelGraph novelGraph

    @Inject
    DataSource dataSource

    @Inject
    List<JdbcSchemaContributor> contributors

    @Inject
    DataJdbcConfiguration configuration

    @Inject
    RuntimeEntityRegistry entityRegistry

    void setup() {
        sql("DELETE FROM fans_likes_films", "DELETE FROM fans_likes_novels", "DELETE FROM test.novels_sequel",
            "DELETE FROM fans", "DELETE FROM films", "DELETE FROM test.novels")
        fanGraph.save(new Fan(1L, "Ged", [], []))
        fanGraph.save(new Fan(2L, "Tenar", [], []))
        filmGraph.save(new Film(10L, "Tales from Earthsea", []))
        novelGraph.save(new Novel(20L, "Tehanu", [], []))
        novelGraph.save(new Novel(21L, "The Other Wind", [], []))
        sql("INSERT INTO fans_likes_films (fanId, filmsId) VALUES (1, 10)",
            "INSERT INTO fans_likes_novels (fanId, novelsId) VALUES (2, 20)",
            "INSERT INTO test.novels_sequel (novelId, sequelsId) VALUES (20, 21)")
    }

    void "a relationship type with several targets keeps an edge table for each"() {
        expect:
        query("SELECT table_name FROM user_tables").containsAll(["FANS_LIKES_FILMS", "FANS_LIKES_NOVELS"])
        fanGraph.findByFilmsTitle("Tales from Earthsea")*.firstName() == ["Ged"]
        fanGraph.findByNovelsTitle("Tehanu")*.firstName() == ["Tenar"]
    }

    void "both sides of a relationship read one edge table"() {
        expect:
        !query("SELECT table_name FROM user_tables").contains("FILMS_LIKES")
        filmGraph.findByFansFirstName("Ged")*.title() == ["Tales from Earthsea"]
        fanGraph.findByFilmsTitle("Tales from Earthsea").size() == 1

        and: "a node related to its own kind, whichever side is declared first"
        novelGraph.findBySequelsTitle("The Other Wind")*.title() == ["Tehanu"]
        novelGraph.findByPrequelsTitle("Tehanu")*.title() == ["The Other Wind"]
    }

    void "a property with a name that is not plain is exposed under that name"() {
        expect:
        fanGraph.findByFirstName("Ged")*.id() == [1L]
        createStatements(Fan, Film, Novel).any { it.contains('"FIRST NAME" AS "first name"') }
    }

    void "the tables of an entity in a schema are qualified"() {
        given:
        def entities = [Fan, Film, Novel]
        def graph = createStatements(*entities).find { it.startsWith("CREATE PROPERTY GRAPH") }

        expect:
        createStatements(*entities).any { it.startsWith('CREATE TABLE "TEST"."NOVELS_SEQUEL" ') }
        graph.contains('"TEST"."NOVELS" KEY ("ID") LABEL Novel')
        graph.contains('"TEST"."NOVELS_SEQUEL" KEY (')
        graph.contains('REFERENCES "NOVELS" ("ID")')
        contributor(*entities).buildDropStatements(configuration, entities.collect { entityRegistry.getEntity(it) })
            .containsAll(['DROP TABLE "TEST"."NOVELS_SEQUEL"', 'DROP TABLE "TEST"."NOVELS"'])
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
        contributor(types).buildCreateStatements(configuration, types.collect { entityRegistry.getEntity(it) })
    }

    private JdbcSchemaContributor contributor(Class<?>... types) {
        def entities = types.collect { entityRegistry.getEntity(it) }
        contributors.find { !it.selectEntities(configuration, entities).isEmpty() }
    }

    private void sql(String... statements) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                statements.each { statement.execute(it) }
            }
        }
    }

    private List<String> query(String query) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.createStatement().withCloseable { statement ->
                firstColumn(statement.executeQuery(query))
            }
        }
    }

    private static List<String> firstColumn(ResultSet rs) {
        rs.withCloseable {
            List<String> values = []
            while (rs.next()) {
                values << rs.getString(1)
            }
            values
        }
    }
}
