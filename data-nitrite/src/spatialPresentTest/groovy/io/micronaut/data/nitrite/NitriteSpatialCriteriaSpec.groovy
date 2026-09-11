package io.micronaut.data.nitrite

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaQuery
import io.micronaut.data.model.jpa.criteria.PersistentEntityRoot
import io.micronaut.data.nitrite.model.IndexedBook
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import spock.lang.Shared
import spock.lang.Specification

/**
 * Criteria-path coverage for {@code NitritePredicateVisitor} spatial visit methods
 * ({@code visitGeoWithin} / {@code visitGeoIntersects} / {@code visitNear}).
 *
 * <p>The derived / {@code @Query} spatial tests in {@code NitriteSpatialSpec} route
 * through {@code SpatialFilterFactory} and never reach these runtime criteria
 * visitor methods — only the criteria-builder API exercises them. JTS geometry
 * types are {@code compileOnly} in the main source set and only present here, so
 * this spec must live in the {@code spatialPresentTest} source set.
 *
 * <p>{@code cb.literal(...)} is bound as a parameter ({@code $mn_qp:N}) by
 * {@code RuntimeCriteriaBuilder}, so the geometry value surfaces as a placeholder
 * in the produced filter JSON.
 */
class NitriteSpatialCriteriaSpec extends Specification {

    @Shared
    GeometryFactory factory = new GeometryFactory()

    PersistentEntityCriteriaBuilder criteriaBuilder

    void setup() {
        criteriaBuilder = new RuntimeCriteriaBuilder()
    }

    void "test geoWithin via criteria builds \$within filter"() {
        given:
            PersistentEntityCriteriaQuery query = criteriaBuilder.createQuery()
            PersistentEntityRoot root = query.from(IndexedBook)
            def polygon = factory.createPolygon([
                new Coordinate(-74.5, 40.5),
                new Coordinate(-73.5, 40.5),
                new Coordinate(-73.5, 41.0),
                new Coordinate(-74.5, 41.0),
                new Coordinate(-74.5, 40.5)
            ] as Coordinate[])
            query.where(criteriaBuilder.geoWithin(root.get("location"), criteriaBuilder.literal(polygon)))

        expect:
            getQuery(query) == '''{location:{$within:{$mn_qp:0}}}'''
    }

    void "test geoIntersects via criteria builds \$intersects filter"() {
        given:
            PersistentEntityCriteriaQuery query = criteriaBuilder.createQuery()
            PersistentEntityRoot root = query.from(IndexedBook)
            def line = factory.createLineString([
                new Coordinate(-74.5, 40.5),
                new Coordinate(-73.5, 41.0)
            ] as Coordinate[])
            query.where(criteriaBuilder.geoIntersects(root.get("location"), criteriaBuilder.literal(line)))

        expect:
            getQuery(query) == '''{location:{$intersects:{$mn_qp:0}}}'''
    }

    void "test near via criteria builds \$near filter with center and distance"() {
        given:
            PersistentEntityCriteriaQuery query = criteriaBuilder.createQuery()
            PersistentEntityRoot root = query.from(IndexedBook)
            def point = factory.createPoint(new Coordinate(-74.0060, 40.7128))
            query.where(criteriaBuilder.near(root.get("location"), criteriaBuilder.literal(point), criteriaBuilder.literal(0.5d)))

        expect:
            getQuery(query) == '''{location:{$near:{center:{$mn_qp:0},distance:{$mn_qp:1}}}}'''
    }

    private static String getQuery(PersistentEntityCriteriaQuery<Object> query) {
        return query.build(AnnotationMetadata.EMPTY_METADATA, new NitriteQueryBuilder()).getQuery()
    }
}
