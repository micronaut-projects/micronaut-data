package example

import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class GeographyRepositorySpec extends Specification {

    @Inject
    GeographyEntityJsonRepository geographyEntityJsonRepository

    @Inject
    GeographyEntityWktRepository geographyEntityWktRepository

    void "test CRUD when JSON conversion used"() {
        given:
        def entity = new GeographyEntityJson(
                point: new Point(10, 11),
                multiPoint: new MultiPoint([new Point(21, 22), new Point(23, 24)]))

        when:
        def saved = geographyEntityJsonRepository.save(entity)
        def found = geographyEntityJsonRepository.findById(saved.id).orElse(null)

        then:
        saved.id != null
        found.point == entity.point
        found.multiPoint == entity.multiPoint

        when:
        entity.point = new Point(31, 32)
        entity.multiPoint = new MultiPoint([new Point(41, 42), new Point(43, 44)])
        geographyEntityJsonRepository.update(entity)
        found = geographyEntityJsonRepository.findById(saved.id).orElse(null)

        then:
        found.point == entity.point
        found.multiPoint == entity.multiPoint

        when:
        entity.multiPoint = null
        geographyEntityJsonRepository.update(entity)
        found = geographyEntityJsonRepository.findById(saved.id).orElse(null)

        then:
        found.point == entity.point
        found.multiPoint == null
    }

    void "test CRUD when WKT conversion used"() {
        given:
        def entity = new GeographyEntityWkt(
                point: new Point(10, 11),
                multiPoint: new MultiPoint([new Point(21, 22), new Point(23, 24)]))

        when:
        def saved = geographyEntityWktRepository.save(entity)
        def found = geographyEntityWktRepository.findById(saved.id).orElse(null)

        then:
        saved.id != null
        found.point == entity.point
        found.multiPoint == entity.multiPoint

        when:
        entity.point = new Point(31, 32)
        entity.multiPoint = new MultiPoint([new Point(41, 42), new Point(43, 44)])
        geographyEntityWktRepository.update(entity)
        found = geographyEntityWktRepository.findById(saved.id).orElse(null)

        then:
        found.point == entity.point
        found.multiPoint == entity.multiPoint

        when:
        entity.multiPoint = null
        geographyEntityWktRepository.update(entity)
        found = geographyEntityWktRepository.findById(saved.id).orElse(null)

        then:
        found.point == entity.point
        found.multiPoint == null
    }
}
