package io.micronaut.data.mongodb.index.validation.georulesresolution

import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.mongodb.common.MongoEntityIndexes
import spock.lang.Shared
import spock.lang.Specification

class MongoGeoRulesResolutionSpec extends Specification {

    @Shared
    Map<Class<?>, RuntimePersistentEntity<?>> entities = [:]

    void 'resolves 2d index for list-backed legacy coordinates'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(ListBacked2dGeoEntity)).indexes
        def index = indexes.find { it.name() == 'list_backed_2d_geo_idx' }

        then:
        index != null
        index.fields()[0].kind() == '2d'
    }

    void 'resolves 2dsphere index for array-backed legacy coordinates'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(ArrayBacked2dsphereGeoEntity)).indexes
        def index = indexes.find { it.name() == 'array_backed_2dsphere_geo_idx' }

        then:
        index != null
        index.fields()[0].kind() == '2dsphere'
    }

    void 'allows simple collation for 2d index'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(SimpleCollation2dGeoEntity)).indexes
        def index = indexes.find { it.name() == 'simple_collation_2d_geo_idx' }

        then:
        index != null
        index.collation() == '{"locale": "simple"}'
    }

    void 'fails when 2d index uses non-simple collation'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(NonSimpleCollation2dGeoEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('supports only collation {"locale":"simple"}')
    }

    void 'fails when compound 2d index does not declare 2d field first'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(Compound2dGeoFieldNotFirstEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('must declare exactly two fields with the 2d field first')
    }

    void 'fails when compound 2d index declares more than two fields'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(Compound2dTooManyFieldsEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('must declare exactly two fields with the 2d field first')
    }

    void 'fails when compound geospatial field uses unsupported property type'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(InvalidCompoundGeoTypeEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('requires a supported MongoDB GeoJSON type')
    }

    void 'fails when compound 2d index uses non-simple collation'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(NonSimpleCollationCompound2dGeoEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('supports only collation {"locale":"simple"}')
    }

    void 'resolves compound 2d index for list-backed legacy coordinates'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(CompoundListBacked2dGeoEntity)).indexes
        def index = indexes.find { it.name() == 'compound_list_backed_2d_geo_idx' }

        then:
        index != null
        index.fields()[0].kind() == '2d'
        index.fields()[1].order() == 1
    }

    private RuntimePersistentEntity<?> getRuntimePersistentEntity(Class<?> type) {
        RuntimePersistentEntity<?> entity = entities.get(type)
        if (entity == null) {
            entity = new RuntimePersistentEntity<Object>(type) {
                @Override
                protected RuntimePersistentEntity getEntity(Class t) {
                    return getRuntimePersistentEntity(t)
                }
            }
            entities.put(type, entity)
        }
        return entity
    }
}

@MappedEntity('list_backed_2d_geo_entities')
class ListBacked2dGeoEntity {
    @MongoGeoIndexed(name = 'list_backed_2d_geo_idx', type = MongoGeoIndexType.GEO_2D)
    List<Double> location
}

@MappedEntity('array_backed_2dsphere_geo_entities')
class ArrayBacked2dsphereGeoEntity {
    @MongoGeoIndexed(name = 'array_backed_2dsphere_geo_idx')
    Double[] location
}

@MappedEntity('simple_collation_2d_geo_entities')
class SimpleCollation2dGeoEntity {
    @MongoGeoIndexed(name = 'simple_collation_2d_geo_idx', type = MongoGeoIndexType.GEO_2D, collation = '{ "locale": "simple" }')
    List<Double> location
}

@MappedEntity('non_simple_collation_2d_geo_entities')
class NonSimpleCollation2dGeoEntity {
    @MongoGeoIndexed(name = 'non_simple_collation_2d_geo_idx', type = MongoGeoIndexType.GEO_2D, collation = '{ "locale": "en", "strength": 2 }')
    List<Double> location
}

@MongoCompoundIndex(
        name = 'compound_2d_geo_field_not_first_idx',
        fields = [
                @MongoCompoundIndexField('name'),
                @MongoCompoundIndexField(value = 'location', geo = true, geoType = MongoGeoIndexType.GEO_2D)
        ]
)
@MappedEntity('compound_2d_geo_field_not_first_entities')
class Compound2dGeoFieldNotFirstEntity {
    String name
    List<Double> location
}

@MongoCompoundIndex(
        name = 'compound_2d_too_many_fields_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location', geo = true, geoType = MongoGeoIndexType.GEO_2D),
                @MongoCompoundIndexField('name'),
                @MongoCompoundIndexField('region')
        ]
)
@MappedEntity('compound_2d_too_many_fields_entities')
class Compound2dTooManyFieldsEntity {
    List<Double> location
    String name
    String region
}

@MongoCompoundIndex(
        name = 'invalid_compound_geo_type_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location', geo = true),
                @MongoCompoundIndexField('name')
        ]
)
@MappedEntity('invalid_compound_geo_type_entities')
class InvalidCompoundGeoTypeEntity {
    String location
    String name
}

@MongoCompoundIndex(
        name = 'non_simple_collation_compound_2d_geo_idx',
        collation = '{ "locale": "en", "strength": 2 }',
        fields = [
                @MongoCompoundIndexField(value = 'location', geo = true, geoType = MongoGeoIndexType.GEO_2D),
                @MongoCompoundIndexField('name')
        ]
)
@MappedEntity('non_simple_collation_compound_2d_geo_entities')
class NonSimpleCollationCompound2dGeoEntity {
    List<Double> location
    String name
}

@MongoCompoundIndex(
        name = 'compound_list_backed_2d_geo_idx',
        fields = [
                @MongoCompoundIndexField(value = 'location', geo = true, geoType = MongoGeoIndexType.GEO_2D),
                @MongoCompoundIndexField('name')
        ]
)
@MappedEntity('compound_list_backed_2d_geo_entities')
class CompoundListBacked2dGeoEntity {
    List<Double> location
    String name
}
