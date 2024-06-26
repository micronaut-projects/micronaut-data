package io.micronaut.data.jdbc.h2.one2many

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

/**
 * Test case when an entity has more than one one-to-many collection of the same entity.
 */
@MicronautTest
@H2DBProperties
class MultipleOneToManySpec extends Specification {

    @Shared
    @Inject
    MatchRepository matchRepository

    @Shared
    @Inject
    TeamRepository teamRepository

    void "test multiple one to many"() {
        given:
        def liverpool = teamRepository.save(new Team(name: "Liverpool"))
        def manchester = teamRepository.save(new Team(name: "Manchester United"))
        def westHam = teamRepository.save(new Team(name: "West Ham United"))
        def matchJune1st = matchRepository.save(new Match(date: createDate(2024, 6, 1), location: "Liverpool",
            homeTeam: liverpool, awayTeam: manchester))
        matchRepository.save(new Match(date: createDate(2024, 6, 3), location: "Liverpool",
                homeTeam: liverpool, awayTeam: westHam))
        matchRepository.save(new Match(date: createDate(2024, 6, 4), location: "Manchester",
                homeTeam: manchester, awayTeam: liverpool))
        matchRepository.save(new Match(date: createDate(2024, 6, 5), location: "London",
                homeTeam: westHam, awayTeam: manchester))
        when:
        def match = matchRepository.getById(matchJune1st.id)
        then:
        match
        match.date == matchJune1st.date
        match.location == matchJune1st.location
        match.homeTeam != match.awayTeam
        match.homeTeam.id == liverpool.id
        match.awayTeam.id == manchester.id
        when:
        def team = teamRepository.getById(liverpool.id)
        then:
        team
        team.id == liverpool.id
        team.name == liverpool.name
        team.homeMatches.size() == 2
        team.awayMatches.size() == 1
        team.homeMatches[0].awayTeam != team.homeMatches[0].homeTeam
        team.homeMatches[1].awayTeam != team.homeMatches[1].homeTeam
        team.awayMatches[0].awayTeam != team.awayMatches[0].homeTeam
    }

    Instant createDate(int year, int month, int day) {
        Calendar calendar = Calendar.instance
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.toInstant()
    }
}

@MappedEntity
class Team {
    @Id
    @GeneratedValue
    Long id

    String name

    @OneToMany(mappedBy = "homeTeam")
    Set<Match> homeMatches

    @OneToMany(mappedBy = "awayTeam")
    Set<Match> awayMatches

    boolean equals(o) {
        if (this.is(o)) return true
        if (o == null || getClass() != o.class) return false

        Team team = (Team) o

        if (name != team.name) return false

        return true
    }

    int hashCode() {
        return (name != null ? name.hashCode() : 0)
    }
}

@MappedEntity
class Match {
    @Id
    @GeneratedValue
    Long id

    Instant date

    String location

    @ManyToOne(optional = false)
    Team homeTeam

    @ManyToOne(optional = false)
    Team awayTeam

    boolean equals(o) {
        if (this.is(o)) return true
        if (o == null || getClass() != o.class) return false

        Match match = (Match) o

        if (date != match.date) return false
        if (location != match.location) return false

        return true
    }

    int hashCode() {
        int result
        result = (date != null ? date.hashCode() : 0)
        result = 31 * result + (location != null ? location.hashCode() : 0)
        return result
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface TeamRepository extends CrudRepository<Team, Long> {
    @Join(value = "homeMatches", type = Join.Type.LEFT_FETCH)
    @Join(value = "awayMatches", type = Join.Type.LEFT_FETCH)
    Team getById(Long id);
}

@JdbcRepository(dialect = Dialect.H2)
interface MatchRepository extends CrudRepository<Match, Long> {
    @Join(value = "homeTeam", type = Join.Type.LEFT_FETCH)
    @Join(value = "awayTeam", type = Join.Type.LEFT_FETCH)
    Match getById(Long id);
}
