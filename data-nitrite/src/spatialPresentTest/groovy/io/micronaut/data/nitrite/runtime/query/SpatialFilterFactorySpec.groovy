package io.micronaut.data.nitrite.runtime.query

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Inject
import org.dizitart.no2.Nitrite
import org.dizitart.no2.filters.Filter
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import spock.lang.Specification

@MicronautTest(transactional = false)
class SpatialFilterFactorySpec extends Specification {

    @Inject ConversionService conversionService
    @Inject ObjectMapper objectMapper
    @Inject Nitrite nitrite
    @Inject RuntimeEntityRegistry runtimeEntityRegistry

    def "test SpatialFilterFactory branches"() {
        given:
        def entityMapper = new NitriteEntityMapper(conversionService, objectMapper, runtimeEntityRegistry)
        def valueResolver = new ValueResolver(entityMapper)
        def factory = new SpatialFilterFactory(entityMapper, valueResolver)

        def geometryFactory = new GeometryFactory()
        def coordinate = new Coordinate(1.0, 2.0)
        def jtsPoint = geometryFactory.createPoint(coordinate)
        def jtsPolygon = geometryFactory.createPolygon([
            new Coordinate(0.0, 0.0),
            new Coordinate(0.0, 5.0),
            new Coordinate(5.0, 5.0),
            new Coordinate(5.0, 0.0),
            new Coordinate(0.0, 0.0)
        ] as Coordinate[])

        def geoPoint = null
        try {
            geoPoint = Class.forName("org.dizitart.no2.spatial.GeoPoint").getConstructor(double, double).newInstance(1.0, 2.0)
        } catch (Throwable ignored) {
        }

        expect: "buildNearFilter guards"
        factory.buildNearFilter("loc", "notAMap", [] as Object[], [:]) == Filter.ALL
        factory.buildNearFilter("loc", [center: null, distance: 10.0], [] as Object[], [:]) == Filter.ALL

        and: "buildNearFilter with different distance types"
        factory.buildNearFilter("loc", [center: coordinate, distance: "notANumber"], [] as Object[], [:]).toString().contains("near")

        and: "buildNearFilter with different center types"
        // Center is JTS Coordinate
        def mapCoord = [center: coordinate, distance: 10.0]
        factory.buildNearFilter("loc", mapCoord, [] as Object[], [:]).toString().contains("near")

        // Center is JTS Point
        def mapJtsPoint = [center: jtsPoint, distance: 10.0]
        factory.buildNearFilter("loc", mapJtsPoint, [] as Object[], [:]).toString().contains("near")

        if (geoPoint != null) {
            // Center is GeoPoint
            def mapGeoPoint = [center: geoPoint, distance: 10.0]
            assert factory.buildNearFilter("loc", mapGeoPoint, [] as Object[], [:]).toString().contains("near")
        }
        
        // Center is JTS Geometry (Polygon)
        def mapJtsPolygon = [center: jtsPolygon, distance: 10.0]
        factory.buildNearFilter("loc", mapJtsPolygon, [] as Object[], [:]).toString().contains("near")

        when: "Center is JTS Geometry with null coordinate (empty polygon)"
        def emptyPolygon = geometryFactory.createPolygon([] as Coordinate[])
        factory.buildNearFilter("loc", [center: emptyPolygon, distance: 10.0], [] as Object[], [:])
        then:
        def e0 = thrown(Exception)
        e0.message.contains("Unsupported center type")

        expect: "createSpatialFilter guards"
        factory.createSpatialFilter("loc", null, "within") == Filter.ALL

        and: "createSpatialFilter with different geometry types"
        factory.createSpatialFilter("loc", jtsPolygon, "within").toString().contains("within")
        if (geoPoint != null) {
            assert factory.createSpatialFilter("loc", geoPoint, "within").toString().contains("within")
        }
        
        when: "unsupported center"
        factory.buildNearFilter("loc", [center: "unsupported", distance: 10], [] as Object[], [:])
        then:
        def e1 = thrown(Exception)
        e1.message.contains("Unsupported center type")
        
        when: "unsupported geometry"
        factory.createSpatialFilter("loc", "unsupported", "within")
        then:
        def e2 = thrown(Exception)
        e2.message.contains("Unsupported geometry type")
    }
}
