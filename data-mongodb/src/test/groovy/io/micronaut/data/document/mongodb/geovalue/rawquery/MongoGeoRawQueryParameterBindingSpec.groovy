package io.micronaut.data.document.mongodb.geovalue.rawquery

import io.micronaut.context.ApplicationContext
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.geo.MongoGeoMultiPoint
import io.micronaut.data.mongodb.geo.MongoGeoPoint
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MongoGeoRawQueryParameterBindingSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoGeoRawQueryEntityRepository repository

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.geovalue.rawquery']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        repository = applicationContext.getBean(MongoGeoRawQueryEntityRepository)
    }

    void setup() {
        repository.deleteAll()
    }

    void 'binds modeled geospatial parameter in @MongoFindQuery as GeoJSON'() {
        given:
        def multiPoint = new MongoGeoMultiPoint([
                new MongoGeoPoint(-73.99d, 40.75d),
                new MongoGeoPoint(-73.98d, 40.74d),
                new MongoGeoPoint(-73.97d, 40.73d)
        ])

        when:
        def saved = repository.save(new MongoGeoRawQueryEntity(locations: multiPoint))
        def loaded = repository.findByIntersectsGeometry(new MongoGeoPoint(-73.99d, 40.75d))

        then:
        loaded.present
        loaded.get().id == saved.id
    }

    void 'binds modeled geospatial parameter typed as Object in @MongoFindQuery as GeoJSON'() {
        given:
        def multiPoint = new MongoGeoMultiPoint([
                new MongoGeoPoint(-73.99d, 40.75d),
                new MongoGeoPoint(-73.98d, 40.74d),
                new MongoGeoPoint(-73.97d, 40.73d)
        ])

        when:
        def saved = repository.save(new MongoGeoRawQueryEntity(locations: multiPoint))
        def loaded = repository.findByIntersectsGeometryObject(new MongoGeoPoint(-73.99d, 40.75d))

        then:
        loaded.present
        loaded.get().id == saved.id
    }

    void 'keeps null raw-query geospatial parameter as null binding'() {
        given:
        def multiPoint = new MongoGeoMultiPoint([
                new MongoGeoPoint(-73.99d, 40.75d),
                new MongoGeoPoint(-73.98d, 40.74d),
                new MongoGeoPoint(-73.97d, 40.73d)
        ])
        repository.save(new MongoGeoRawQueryEntity(locations: multiPoint))

        when:
        def loaded = repository.findByLocationsRawNullable(null)

        then:
        !loaded.present
    }

    void 'respects explicit parameter converter and does not apply implicit geospatial fallback'() {
        given:
        def multiPoint = new MongoGeoMultiPoint([
                new MongoGeoPoint(-73.99d, 40.75d),
                new MongoGeoPoint(-73.98d, 40.74d),
                new MongoGeoPoint(-73.97d, 40.73d)
        ])
        repository.save(new MongoGeoRawQueryEntity(locations: multiPoint))

        when:
        repository.findByIntersectsGeometryWithExplicitConverter(new MongoGeoPoint(-73.99d, 40.75d))

        then:
        def e = thrown(Exception)
        e.message.contains('Longitude/latitude is out of bounds')
    }
}
