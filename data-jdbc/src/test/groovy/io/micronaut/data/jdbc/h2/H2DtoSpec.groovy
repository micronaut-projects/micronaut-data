package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.NamingStrategy
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.naming.NamingStrategies
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class H2DtoSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    ThingRepository thingRepository = applicationContext.getBean(ThingRepository)

    void 'test dtos'() {
        given:
            Thing thing = new Thing(name: "Test", score: 123, site: "XYZ")
        when:
            thingRepository.save(thing)
        then:
            thing.id
        when:
            def things = thingRepository.findThingDTOsByThingId(thing.id)
        then:
            things.size() == 1
            things[0].thingId == thing.id
            things[0].thingName == "Test"
    }

}

@JdbcRepository(dialect = Dialect.H2)
interface ThingRepository extends CrudRepository<Thing, Long> {

    @Query("""
      SELECT thing.id AS thingId, thing.name AS thingName
      FROM the_things thing
      WHERE thing.id = :id
    """)
    List<ThingDTO> findThingDTOsByThingId(Long id)

}

@MappedEntity(value = "the_things", namingStrategy = NamingStrategies.Raw)
class Thing {
    @Id
    @GeneratedValue
    Long id
    String name
    Integer score
    String site
}

@Introspected
@NamingStrategy(NamingStrategies.Raw)
class ThingDTO {
    Integer thingId
    String thingName
}