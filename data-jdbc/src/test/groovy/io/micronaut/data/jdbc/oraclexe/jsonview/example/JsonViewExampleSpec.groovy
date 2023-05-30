package io.micronaut.data.jdbc.oraclexe.jsonview.example

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.OracleContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JsonViewExampleSpec extends Specification implements TestPropertyProvider {

    @AutoCleanup("stop")
    @Shared
    OracleContainer container = createContainer()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    TeamRepository getTeamRepository() {
        return context.getBean(TeamRepository)
    }

    DriverRepository getDriverRepository() {
        return context.getBean(DriverRepository)
    }

    RaceRepository getRaceRepository() {
        return context.getBean(RaceRepository)
    }

    DriverRaceMapRepository getDriverRaceMapRepository() {
        return context.getBean(DriverRaceMapRepository)
    }

    DriverViewRepository getDriverViewRepository() {
        return context.getBean(DriverViewRepository)
    }

    TeamViewRepository getTeamViewRepository() {
        return context.getBean(TeamViewRepository)
    }

    @Override
    Map<String, String> getProperties() {
        if (container == null) {
            container = createContainer()
        }
        container.start()
        def prefix = 'datasources.default'
        return [
                (prefix + '.url')               : container.getJdbcUrl(),
                (prefix + '.username')          : container.getUsername(),
                (prefix + '.password')          : container.getPassword(),
                // Cannot create JSON view during schema creation, works via init script
                (prefix + '.schema-generate')   : 'CREATE',
                (prefix + '.dialect')           : Dialect.ORACLE,
                (prefix + '.packages')          : getClass().package.name
        ] as Map<String, String>
    }

    /**
     * Test finding data using view from records created in source tables
     */
    def "save entities and load views"() {
        when:
        def team1 = new Team()
        team1.name = "McLaren"
        team1.points = 90
        teamRepository.save(team1)
        def team2 = new Team()
        team2.name = "Ferrari"
        team2.points = 105
        teamRepository.save(team2)
        def driver1 = new Driver()
        driver1.points = 30
        driver1.name = "Lando Norris"
        driver1.team = team1
        driverRepository.save(driver1)
        def driver2 = new Driver()
        driver2.points = 20
        driver2.name = "Oscar Piastri"
        driver2.team = team1
        driverRepository.save(driver2)
        def driver3 = new Driver()
        driver3.name = "Charles Leclerc"
        driver3.points = 15
        driver3.team = team2
        driverRepository.save(driver3)
        def race = new Race()
        race.name = "Bahrain"
        race.laps = 57
        race.podium = "some random data"
        race.raceDate = new Date(2023, 6, 1)
        raceRepository.save(race)
        def driverRaceMap1 = new DriverRaceMap()
        driverRaceMap1.race = race
        driverRaceMap1.position = 1
        driverRaceMap1.driver = driver1
        driverRaceMapRepository.save(driverRaceMap1)
        def driverRaceMap2 = new DriverRaceMap()
        driverRaceMap2.race = race
        driverRaceMap2.position = 2
        driverRaceMap2.driver = driver2
        driverRaceMapRepository.save(driverRaceMap2)
        def driverRaceMap3 = new DriverRaceMap()
        driverRaceMap3.race = race
        driverRaceMap3.position = 3
        driverRaceMap3.driver = driver3
        driverRaceMapRepository.save(driverRaceMap3)

        def optTeamView1 = teamViewRepository.findById(team1.teamId)
        def optTeamView2 = teamViewRepository.findById(team2.teamId)
        def driverView1 = driverViewRepository.findById(driver1.driverId)
        def driverView3 = driverViewRepository.findById(driver3.driverId)
        then:
        optTeamView1.present
        optTeamView2.present
        driverView1.present
        driverView3.present
    }

    static OracleContainer createContainer() {
        return new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:latest-faststart").asCompatibleSubstituteFor("gvenzl/oracle-xe"))
                .withDatabaseName("test").withInitScript("./oracle-json-view-example-init.sql")
    }
}
