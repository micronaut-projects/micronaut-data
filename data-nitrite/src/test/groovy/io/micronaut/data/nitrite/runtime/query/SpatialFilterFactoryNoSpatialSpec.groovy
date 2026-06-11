package io.micronaut.data.nitrite.runtime.query

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import spock.lang.Specification

@MicronautTest(transactional = false)
class SpatialFilterFactoryNoSpatialSpec extends Specification {

    @Inject ConversionService conversionService
    @Inject ObjectMapper objectMapper
    @Inject Nitrite nitrite
    @Inject RuntimeEntityRegistry runtimeEntityRegistry

    def "test SpatialFilterFactory missing library"() {
        given:
        def entityMapper = new NitriteEntityMapper(conversionService, objectMapper, nitrite.getConfig().nitriteMapper(), runtimeEntityRegistry)
        def valueResolver = new ValueResolver(entityMapper)
        def factory = new SpatialFilterFactory(entityMapper, valueResolver)

        when: "missing nitrite-spatial for near"
        factory.buildNearFilter("loc", [center: "any", distance: 10], [] as Object[], [:])
        then:
        def e1 = thrown(DataAccessException)
        e1.message.contains("nitrite-spatial on the classpath")

        when: "missing nitrite-spatial for within"
        factory.createSpatialFilter("loc", "any", "within")
        then:
        def e2 = thrown(DataAccessException)
        e2.message.contains("nitrite-spatial on the classpath")
    }
}
