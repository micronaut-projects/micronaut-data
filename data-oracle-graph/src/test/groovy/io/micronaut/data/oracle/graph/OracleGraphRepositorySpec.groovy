package io.micronaut.data.oracle.graph

import io.micronaut.context.annotation.Property
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.graph.tck.AbstractGraphRepositorySpec
import io.micronaut.data.graph.tck.entities.Movie
import io.micronaut.data.graph.tck.entities.Person
import io.micronaut.data.graph.tck.repositories.ColleagueRepository
import io.micronaut.data.graph.tck.repositories.FriendRepository
import io.micronaut.data.oracle.graph.tck.OracleMovieRepository
import io.micronaut.data.oracle.graph.tck.OracleMovieSpecificationRepository
import io.micronaut.data.oracle.graph.tck.OraclePersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import spock.lang.PendingFeature

import javax.sql.DataSource
import java.sql.Connection
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs the graph TCK on Oracle Database 23ai, over the tables and property graph that schema generation creates for
 * the TCK entities. Oracle repositories write node rows only and query with SQL/PGQ: the relationships the specs
 * need are written to the generated edge tables with SQL, so the finders that traverse them run, while the features
 * that need relationship writes through save, fetch joins, path results or Cypher are skipped.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.schema-generate", value = "CREATE_DROP")
@Property(name = "datasources.default.packages", value = "io.micronaut.data.graph.tck.entities")
class OracleGraphRepositorySpec extends AbstractGraphRepositorySpec {

    private static final AtomicLong ROLE_IDS = new AtomicLong(1)

    @Inject
    OracleMovieSpecificationRepository movieSpecificationRepository

    @Inject
    OracleMovieRepository movieRepository

    @Inject
    OraclePersonRepository personRepository

    @Inject
    TransactionOperations<Connection> transactionOperations

    @Inject
    DataSource dataSource

    @Override
    FriendRepository getFriendRepository() {
        null
    }

    @Override
    ColleagueRepository getColleagueRepository() {
        null
    }

    @Override
    boolean supportsRelationshipWrites() {
        false
    }

    @Override
    boolean supportsRelationshipFinders() {
        true
    }

    @Override
    boolean supportsRelationshipReads() {
        false
    }

    @Override
    boolean supportsPaths() {
        false
    }

    @Override
    boolean supportsExplicitQueries() {
        false
    }

    @Override
    protected Movie saveMovie(Movie movie) {
        def saved = movieRepository.save(movie)
        movie.directors.each { director ->
            savePersonOnce(director)
            sql('INSERT INTO "MOVIE_DIRECTED" ("MOVIEID", "DIRECTORSID") SELECT ?, "ID" FROM "PERSON" WHERE "NAME" = ?',
                movie.title, director.name)
        }
        movie.roles.each { role ->
            savePersonOnce(role.person)
            sql('INSERT INTO "ROLE" ("ID", "ROLES", "PERSONID", "MOVIEID") SELECT ?, ?, "ID", ? FROM "PERSON" WHERE "NAME" = ?',
                ROLE_IDS.getAndIncrement(), json(role.roles), movie.title, role.person.name)
        }
        saved
    }

    @Override
    protected Person savePerson(Person person) {
        def saved = savePersonOnce(person)
        if (person.mentor != null) {
            savePerson(person.mentor)
            sql('INSERT INTO "PERSON_MENTORED_BY" ("PERSONID", "MENTORID") SELECT a."ID", b."ID" FROM "PERSON" a, "PERSON" b WHERE a."NAME" = ? AND b."NAME" = ?',
                person.name, person.mentor.name)
        }
        saved
    }

    @Override
    void cleanupGraph() {
        // The generated edge tables have no foreign keys and the repositories delete node rows only
        sql('DELETE FROM "ROLE"')
        sql('DELETE FROM "MOVIE_DIRECTED"')
        sql('DELETE FROM "PERSON_MENTORED_BY"')
        movieRepository.deleteAll()
        personRepository.deleteAll()
    }

    @PendingFeature(reason = "The insert of a node with a single relationship writes the MENTORID column that the generated vertex table does not have: the relationship is an edge table")
    void "save a node with a single relationship to its vertex table"() {
        when:
        def saved = personRepository.save(new Person("Trinity", 1967))

        then:
        personRepository.findById(saved.id).get().name == "Trinity"
    }

    /**
     * The same person appears in several movies of the fixtures: the row is written once. The row is written with
     * SQL until the repository insert of a node with a single relationship matches the generated vertex table.
     */
    private Person savePersonOnce(Person person) {
        personRepository.findByName(person.name).orElseGet {
            person.id = UUID.randomUUID()
            sql('INSERT INTO "PERSON" ("ID", "NAME", "BORN") VALUES (?, ?, ?)', person.id.toString(), person.name, person.born)
            person
        }
    }

    /**
     * A JSON array of the role names, for the JSON column of the ACTED_IN edge table.
     */
    private static String json(List<String> values) {
        values == null ? null : '[' + values.collect { '"' + it.replace('"', '\\"') + '"' }.join(',') + ']'
    }

    private void sql(String statement, Object... parameters) {
        DelegatingDataSource.unwrapDataSource(dataSource).connection.withCloseable { connection ->
            connection.prepareStatement(statement).withCloseable { ps ->
                parameters.eachWithIndex { parameter, i -> ps.setObject(i + 1, parameter) }
                ps.executeUpdate()
            }
        }
    }
}
