package io.micronaut.data.jdbc.sqlite.one2many;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.one2many")
class MultipleOneToManyTest {

    @Inject
    MatchRepository matchRepository;

    @Inject
    TeamRepository teamRepository;

    @Test
    void testMultipleOneToMany() {
        Team liverpool = new Team();
        liverpool.setName("Liverpool");
        liverpool = teamRepository.save(liverpool);

        Team manchester = new Team();
        manchester.setName("Manchester United");
        manchester = teamRepository.save(manchester);

        Team westHam = new Team();
        westHam.setName("West Ham United");
        westHam = teamRepository.save(westHam);

        Match matchJune1st = new Match();
        matchJune1st.setDate(createDate(2024, 6, 1));
        matchJune1st.setLocation("Liverpool");
        matchJune1st.setHomeTeam(liverpool);
        matchJune1st.setAwayTeam(manchester);
        matchJune1st = matchRepository.save(matchJune1st);

        Match matchJune3rd = new Match();
        matchJune3rd.setDate(createDate(2024, 6, 3));
        matchJune3rd.setLocation("Liverpool");
        matchJune3rd.setHomeTeam(liverpool);
        matchJune3rd.setAwayTeam(westHam);
        matchRepository.save(matchJune3rd);

        Match matchJune4th = new Match();
        matchJune4th.setDate(createDate(2024, 6, 4));
        matchJune4th.setLocation("Manchester");
        matchJune4th.setHomeTeam(manchester);
        matchJune4th.setAwayTeam(liverpool);
        matchRepository.save(matchJune4th);

        Match matchJune5th = new Match();
        matchJune5th.setDate(createDate(2024, 6, 5));
        matchJune5th.setLocation("London");
        matchJune5th.setHomeTeam(westHam);
        matchJune5th.setAwayTeam(manchester);
        matchRepository.save(matchJune5th);

        Match match = matchRepository.getById(matchJune1st.getId());
        assertNotNull(match);
        assertEquals(matchJune1st.getDate(), match.getDate());
        assertEquals(matchJune1st.getLocation(), match.getLocation());
        assertNotEquals(match.getHomeTeam(), match.getAwayTeam());
        assertEquals(liverpool.getId(), match.getHomeTeam().getId());
        assertEquals(manchester.getId(), match.getAwayTeam().getId());

        Team team = teamRepository.getById(liverpool.getId());
        assertNotNull(team);
        assertEquals(liverpool.getId(), team.getId());
        assertEquals(liverpool.getName(), team.getName());
        assertEquals(2, team.getHomeMatches().size());
        assertEquals(1, team.getAwayMatches().size());
        Match[] homeMatches = team.getHomeMatches().toArray(new Match[0]);
        Match awayMatch = team.getAwayMatches().iterator().next();
        assertNotEquals(homeMatches[0].getAwayTeam(), homeMatches[0].getHomeTeam());
        assertNotEquals(homeMatches[1].getAwayTeam(), homeMatches[1].getHomeTeam());
        assertNotEquals(awayMatch.getAwayTeam(), awayMatch.getHomeTeam());
    }

    private Instant createDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        return calendar.toInstant();
    }
}

@MappedEntity
class Team {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    @OneToMany(mappedBy = "homeTeam")
    private Set<Match> homeMatches;

    @OneToMany(mappedBy = "awayTeam")
    private Set<Match> awayMatches;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    Set<Match> getHomeMatches() {
        return homeMatches;
    }

    void setHomeMatches(Set<Match> homeMatches) {
        this.homeMatches = homeMatches;
    }

    Set<Match> getAwayMatches() {
        return awayMatches;
    }

    void setAwayMatches(Set<Match> awayMatches) {
        this.awayMatches = awayMatches;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Team team)) {
            return false;
        }
        return Objects.equals(name, team.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}

@MappedEntity
class Match {

    @Id
    @GeneratedValue
    private Long id;
    private Instant date;
    private String location;

    @ManyToOne(optional = false)
    private Team homeTeam;

    @ManyToOne(optional = false)
    private Team awayTeam;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    Instant getDate() {
        return date;
    }

    void setDate(Instant date) {
        this.date = date;
    }

    String getLocation() {
        return location;
    }

    void setLocation(String location) {
        this.location = location;
    }

    Team getHomeTeam() {
        return homeTeam;
    }

    void setHomeTeam(Team homeTeam) {
        this.homeTeam = homeTeam;
    }

    Team getAwayTeam() {
        return awayTeam;
    }

    void setAwayTeam(Team awayTeam) {
        this.awayTeam = awayTeam;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Match match)) {
            return false;
        }
        return Objects.equals(date, match.date) && Objects.equals(location, match.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, location);
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface TeamRepository extends CrudRepository<Team, Long> {

    @Join(value = "homeMatches", type = Join.Type.LEFT_FETCH)
    @Join(value = "awayMatches", type = Join.Type.LEFT_FETCH)
    Team getById(Long id);
}

@JdbcRepository(dialect = Dialect.ANSI)
interface MatchRepository extends CrudRepository<Match, Long> {

    @Join(value = "homeTeam", type = Join.Type.LEFT_FETCH)
    @Join(value = "awayTeam", type = Join.Type.LEFT_FETCH)
    Match getById(Long id);
}
