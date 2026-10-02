package example

import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.Point
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class GeometryRepositorySpec extends Specification {

    @Inject
    GeometryEntityJsonRepository geometryEntityJsonRepository

    @Inject
    GeometryEntityWktRepository geometryEntityWktRepository

    void "test CRUD when JSON conversion used"() {
        given:
        def entity = new GeometryEntityJson(
                point: new Point(10, 11),
                multiPoint: new MultiPoint([new Point(21, 22), new Point(23, 24)]))

        when:
        def saved = geometryEntityJsonRepository.save(entity)
        def found = geometryEntityJsonRepository.findById(saved.id).orElse(null)

        then:
        saved.id != null
        found.point == entity.point
        found.multiPoint == entity.multiPoint

        when:
        entity.point = new Point(31, 32)
        entity.multiPoint = new MultiPoint([new Point(41, 42), new Point(43, 44)])
        geometryEntityJsonRepository.update(entity)
        found = geometryEntityJsonRepository.findById(saved.id).orElse(null)

        then:
        found.point == entity.point
        found.multiPoint == entity.multiPoint

        when:
        entity.multiPoint = null
        geometryEntityJsonRepository.update(entity)
        found = geometryEntityJsonRepository.findById(saved.id).orElse(null)

        then:
        found.point == entity.point
        found.multiPoint == null
    }

    void "test CRUD when WKT conversion used"() {
        given:
        def entity = new GeometryEntityWkt(
                point: new Point(10, 11),
                multiPoint: new MultiPoint([new Point(21, 22), new Point(23, 24)]))

        when:
        def saved = geometryEntityWktRepository.save(entity)
        def found = geometryEntityWktRepository.findById(saved.id).orElse(null)

        then:
        saved.id != null
        found.point == entity.point
        found.multiPoint == entity.multiPoint

        when:
        entity.point = new Point(31, 32)
        entity.multiPoint = new MultiPoint([new Point(41, 42), new Point(43, 44)])
        geometryEntityWktRepository.update(entity)
        found = geometryEntityWktRepository.findById(saved.id).orElse(null)

        then:
        found.point == entity.point
        found.multiPoint == entity.multiPoint

        when:
        entity.multiPoint = null
        geometryEntityWktRepository.update(entity)
        found = geometryEntityWktRepository.findById(saved.id).orElse(null)

        then:
        found.point == entity.point
        found.multiPoint == null
    }
}
