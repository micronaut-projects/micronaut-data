package io.micronaut.data.nitrite.runtime.query

import io.micronaut.data.exceptions.DataAccessException
import spock.lang.Specification

class SpatialFilterAbsentSpec extends Specification {

    // SpatialFilterFactory is package-private; accessible from same package.
    // nitrite-spatial / JTS are compileOnly and absent at test runtime,
    // so ClassUtils.isPresent checks return false.
    SpatialFilterFactory factory = new SpatialFilterFactory(null, null)

    def "\$within throws DataAccessException when nitrite-spatial is absent"() {
        when:
        factory.createSpatialFilter("location", new Object(), "within")

        then:
        def ex = thrown(DataAccessException)
        ex.message.contains("nitrite-spatial")
    }

    def "\$intersects throws DataAccessException when nitrite-spatial is absent"() {
        when:
        factory.createSpatialFilter("location", new Object(), "intersects")

        then:
        def ex = thrown(DataAccessException)
        ex.message.contains("nitrite-spatial")
    }

    def "\$near throws DataAccessException when nitrite-spatial is absent"() {
        when:
        factory.buildNearFilter("location", [center: new Object(), distance: 1.0], [] as Object[], [:])

        then:
        def ex = thrown(DataAccessException)
        ex.message.contains("nitrite-spatial")
    }

    def "\$within with null geometry returns Filter.ALL"() {
        expect:
        factory.createSpatialFilter("location", null, "within").is(org.dizitart.no2.filters.Filter.ALL)
    }
}
