package io.micronaut.data.document.mongodb.query

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaBuilder
import io.micronaut.data.mongodb.annotation.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoTextIndexed
import io.micronaut.data.mongodb.geo.MongoGeoPoint
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.QuerySpecification
import jakarta.persistence.criteria.Predicate
import org.jspecify.annotations.NonNull
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MongoCriteriaQueryOperatorsExecutionSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.query']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
    }

    def cleanup() {
        repository.deleteAll()
    }

    void 'criteria text predicate filters persisted entities'() {
        given:
        repository.saveAll([
                new QueryOperatorEntity(description: 'coffee shop downtown', location: new MongoGeoPoint(-73.9857d, 40.7484d)),
                new QueryOperatorEntity(description: 'tea house uptown', location: new MongoGeoPoint(-74.1200d, 40.7200d)),
                new QueryOperatorEntity(description: 'coffee roastery', location: new MongoGeoPoint(-73.9800d, 40.7500d))
        ])

        QuerySpecification<QueryOperatorEntity> specification = { root, query, cb ->
            ((PersistentEntityCriteriaBuilder) cb).text('coffee')
        } as QuerySpecification<QueryOperatorEntity>

        when:
        def results = repository.findAll(specification)

        then:
        results*.description as Set == ['coffee shop downtown', 'coffee roastery'] as Set
    }

    void 'criteria geoWithin and geoIntersects filter persisted entities'() {
        given:
        def inside = new MongoGeoPoint(-73.9857d, 40.7484d)
        def outside = new MongoGeoPoint(-74.3000d, 40.6000d)
        repository.saveAll([
                new QueryOperatorEntity(description: 'inside', location: inside),
                new QueryOperatorEntity(description: 'outside', location: outside)
        ])

        def polygon = [
                type       : 'Polygon',
                coordinates: [[
                        [-74.0500d, 40.7000d],
                        [-74.0500d, 40.8000d],
                        [-73.9000d, 40.8000d],
                        [-73.9000d, 40.7000d],
                        [-74.0500d, 40.7000d]
                ]]
        ]

        def intersectPoint = [
                type       : 'Point',
                coordinates: [-73.9857d, 40.7484d]
        ]

        QuerySpecification<QueryOperatorEntity> withinSpecification = { root, query, cb ->
            ((PersistentEntityCriteriaBuilder) cb).geoWithin(root.get('location'), cb.literal(polygon))
        } as QuerySpecification<QueryOperatorEntity>

        QuerySpecification<QueryOperatorEntity> intersectsSpecification = { root, query, cb ->
            ((PersistentEntityCriteriaBuilder) cb).geoIntersects(root.get('location'), cb.literal(intersectPoint))
        } as QuerySpecification<QueryOperatorEntity>

        when:
        def withinResults = repository.findAll(withinSpecification)
        def intersectsResults = repository.findAll(intersectsSpecification)

        then:
        withinResults*.description == ['inside']
        intersectsResults*.description == ['inside']
    }

    void 'criteria near and nearSphere filter by distance'() {
        given:
        def center = new MongoGeoPoint(-73.9857d, 40.7484d)
        def centerGeometry = [
                type       : 'Point',
                coordinates: [-73.9857d, 40.7484d]
        ]
        repository.saveAll([
                new QueryOperatorEntity(description: 'near', location: center),
                new QueryOperatorEntity(description: 'far', location: new MongoGeoPoint(-74.3000d, 40.6000d))
        ])

        QuerySpecification<QueryOperatorEntity> nearSpecification = { root, query, cb ->
            ((PersistentEntityCriteriaBuilder) cb).near(
                    root.get('location'),
                    cb.literal(centerGeometry),
                    cb.literal(0d),
                    cb.literal(2_000d)
            )
        } as QuerySpecification<QueryOperatorEntity>

        QuerySpecification<QueryOperatorEntity> nearSphereSpecification = { root, query, cb ->
            ((PersistentEntityCriteriaBuilder) cb).nearSphere(
                    root.get('location'),
                    cb.literal(centerGeometry),
                    cb.literal(0d),
                    cb.literal(2_000d)
            )
        } as QuerySpecification<QueryOperatorEntity>

        when:
        def nearResults = repository.findAll(nearSpecification)
        def nearSphereResults = repository.findAll(nearSphereSpecification)

        then:
        nearResults*.description == ['near']
        nearSphereResults*.description == ['near']
    }

    @NonNull
    QueryOperatorRepository getRepository() {
        applicationContext.getBean(QueryOperatorRepository)
    }
}

@MongoRepository
interface QueryOperatorRepository extends CrudRepository<QueryOperatorEntity, String>, JpaSpecificationExecutor<QueryOperatorEntity> {
}

@MappedEntity('query_operator_entities')
class QueryOperatorEntity {
    @Id
    @GeneratedValue
    String id

    @MongoTextIndexed(name = 'query_operator_description_text_idx')
    String description

    @MongoGeoIndexed(name = 'query_operator_location_geo_idx')
    MongoGeoPoint location
}
