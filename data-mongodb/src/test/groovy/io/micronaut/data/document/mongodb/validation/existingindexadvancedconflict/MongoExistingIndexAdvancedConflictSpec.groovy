package io.micronaut.data.document.mongodb.validation.existingindexadvancedconflict

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoGeoIndexType
import io.micronaut.data.mongodb.annotation.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoTextIndexed
import io.micronaut.data.mongodb.annotation.MongoWildcardIndex
import io.micronaut.data.repository.CrudRepository
import org.bson.Document
import spock.lang.Specification

class MongoExistingIndexAdvancedConflictSpec extends Specification implements MongoTestPropertyProvider {

    private static final List<String> CONFLICT_COLLECTIONS = [
            'existing_hidden_conflict_index_entities',
            'existing_sparse_conflict_index_entities',
            'existing_expire_conflict_index_entities',
            'existing_sphere_conflict_index_entities',
            'existing_geo2d_bits_conflict_index_entities',
            'existing_geo2d_minmax_conflict_index_entities',
            'existing_text_default_language_conflict_entities',
            'existing_text_language_override_conflict_entities',
            'existing_wildcard_projection_conflict_entities',
            'existing_storage_engine_conflict_entities'
    ]

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.existingindexadvancedconflict']
    }

    void 'fails fast when existing index has conflicting hidden option'() {
        given:
        prepareExistingIndex('existing_hidden_conflict_index_entities',
                new Document('key', new Document('name', 1))
                        .append('name', 'existing_hidden_conflict_idx')
                        .append('hidden', false)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_hidden_conflict_idx', 'hidden')
    }

    void 'fails fast when existing index has conflicting sparse option'() {
        given:
        prepareExistingIndex('existing_sparse_conflict_index_entities',
                new Document('key', new Document('code', 1))
                        .append('name', 'existing_sparse_conflict_idx')
                        .append('sparse', false)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_sparse_conflict_idx', 'sparse')
    }

    void 'fails fast when existing index has conflicting expireAfterSeconds option'() {
        given:
        prepareExistingIndex('existing_expire_conflict_index_entities',
                new Document('key', new Document('expires_at', 1))
                        .append('name', 'existing_expire_conflict_idx')
                        .append('expireAfterSeconds', 120)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_expire_conflict_idx', 'expireAfterSeconds')
    }

    void 'fails fast when existing 2dsphere index has conflicting sphereVersion option'() {
        given:
        prepareExistingIndex('existing_sphere_conflict_index_entities',
                new Document('key', new Document('location', '2dsphere'))
                        .append('name', 'existing_sphere_conflict_idx')
                        .append('2dsphereIndexVersion', 2)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_sphere_conflict_idx', 'sphereVersion')
    }

    void 'fails fast when existing 2d index has conflicting bits option'() {
        given:
        prepareExistingIndex('existing_geo2d_bits_conflict_index_entities',
                new Document('key', new Document('coordinates', '2d'))
                        .append('name', 'existing_geo2d_bits_conflict_idx')
                        .append('bits', 28)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_geo2d_bits_conflict_idx', 'bits')
    }

    void 'fails fast when existing 2d index has conflicting min and max options'() {
        given:
        prepareExistingIndex('existing_geo2d_minmax_conflict_index_entities',
                new Document('key', new Document('coordinates', '2d'))
                        .append('name', 'existing_geo2d_minmax_conflict_idx')
                        .append('bits', 26)
                        .append('min', -180d)
                        .append('max', 180d)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_geo2d_minmax_conflict_idx', 'min')
        e.message.contains('max')
    }

    void 'fails fast when existing text index has conflicting defaultLanguage option'() {
        given:
        prepareExistingIndex('existing_text_default_language_conflict_entities',
                new Document('key', new Document('title', 'text'))
                        .append('name', 'existing_text_default_language_conflict_idx')
                        .append('default_language', 'english')
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_text_default_language_conflict_idx', 'defaultLanguage')
    }

    void 'fails fast when existing text index has conflicting languageOverride option'() {
        given:
        prepareExistingIndex('existing_text_language_override_conflict_entities',
                new Document('key', new Document('content', 'text'))
                        .append('name', 'existing_text_language_override_conflict_idx')
                        .append('default_language', 'spanish')
                        .append('language_override', 'docLang')
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_text_language_override_conflict_idx', 'languageOverride')
    }

    void 'fails fast when existing wildcard index has conflicting wildcardProjection option'() {
        given:
        prepareExistingIndex('existing_wildcard_projection_conflict_entities',
                new Document('key', new Document('$**', 1))
                        .append('name', 'existing_wildcard_projection_conflict_idx')
                        .append('wildcardProjection', new Document('metadata.secret', 0))
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_wildcard_projection_conflict_idx', 'wildcardProjection')
    }

    void 'fails fast when existing index has conflicting storageEngine option'() {
        given:
        prepareExistingIndex('existing_storage_engine_conflict_entities',
                new Document('key', new Document('code', 1))
                        .append('name', 'existing_storage_engine_conflict_idx')
                        .append('storageEngine', new Document('wiredTiger', new Document('configString', 'block_compressor=zlib')))
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_storage_engine_conflict_idx', 'storageEngine')
    }

    protected void prepareExistingIndex(String collectionName, Document index) {
        ApplicationContext preContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'false'
        ])
        try {
            MongoClient mongoClient = preContext.getBean(MongoClient)
            def database = mongoClient.getDatabase('test')
            CONFLICT_COLLECTIONS.each { database.getCollection(it).drop() }
            def collection = database.getCollection(collectionName)
            database.runCommand(new Document('createIndexes', collectionName)
                    .append('indexes', [index]))

            def existing = collection.listIndexes().find { it.getString('name') == index.getString('name') }
            assert existing != null
        } finally {
            preContext.close()
        }
    }

    protected static void assertManagedConflict(RuntimeException e, String indexName, String optionToken) {
        assert e.message != null
        assert e.message.contains('Conflicting existing MongoDB index')
        assert e.message.contains('desired MongoResolvedIndex')
        assert e.message.contains(indexName)
        assert e.message.contains(optionToken)
    }
}

@MongoRepository
interface ExistingHiddenConflictIndexEntityRepository extends CrudRepository<ExistingHiddenConflictIndexEntity, String> {
}

@MappedEntity('existing_hidden_conflict_index_entities')
class ExistingHiddenConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_hidden_conflict_idx', hidden = true)
    String name
}

@MongoRepository
interface ExistingSparseConflictIndexEntityRepository extends CrudRepository<ExistingSparseConflictIndexEntity, String> {
}

@MappedEntity('existing_sparse_conflict_index_entities')
class ExistingSparseConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_sparse_conflict_idx', sparse = true)
    String code
}

@MongoRepository
interface ExistingExpireConflictIndexEntityRepository extends CrudRepository<ExistingExpireConflictIndexEntity, String> {
}

@MappedEntity('existing_expire_conflict_index_entities')
class ExistingExpireConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_expire_conflict_idx', expireAfterSeconds = 60)
    Date expiresAt
}

@MongoRepository
interface ExistingSphereConflictIndexEntityRepository extends CrudRepository<ExistingSphereConflictIndexEntity, String> {
}

@MappedEntity('existing_sphere_conflict_index_entities')
class ExistingSphereConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'existing_sphere_conflict_idx', sphereVersion = 3)
    Map<String, Object> location
}

@MongoRepository
interface ExistingGeo2dBitsConflictIndexEntityRepository extends CrudRepository<ExistingGeo2dBitsConflictIndexEntity, String> {
}

@MappedEntity('existing_geo2d_bits_conflict_index_entities')
class ExistingGeo2dBitsConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'existing_geo2d_bits_conflict_idx', type = MongoGeoIndexType.GEO_2D, bits = 30)
    Map<String, Object> coordinates
}

@MongoRepository
interface ExistingGeo2dMinMaxConflictIndexEntityRepository extends CrudRepository<ExistingGeo2dMinMaxConflictIndexEntity, String> {
}

@MappedEntity('existing_geo2d_minmax_conflict_index_entities')
class ExistingGeo2dMinMaxConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'existing_geo2d_minmax_conflict_idx', type = MongoGeoIndexType.GEO_2D, bits = 26, min = -90d, max = 90d)
    Map<String, Object> coordinates
}

@MongoRepository
interface ExistingTextDefaultLanguageConflictEntityRepository extends CrudRepository<ExistingTextDefaultLanguageConflictEntity, String> {
}

@MappedEntity('existing_text_default_language_conflict_entities')
class ExistingTextDefaultLanguageConflictEntity {
    @Id
    @GeneratedValue
    String id

    @MongoTextIndexed(name = 'existing_text_default_language_conflict_idx', defaultLanguage = 'spanish')
    String title
}

@MongoRepository
interface ExistingTextLanguageOverrideConflictEntityRepository extends CrudRepository<ExistingTextLanguageOverrideConflictEntity, String> {
}

@MappedEntity('existing_text_language_override_conflict_entities')
class ExistingTextLanguageOverrideConflictEntity {
    @Id
    @GeneratedValue
    String id

    @MongoTextIndexed(name = 'existing_text_language_override_conflict_idx', defaultLanguage = 'spanish', languageOverride = 'docLocale')
    String content
}

@MongoRepository
interface ExistingWildcardProjectionConflictEntityRepository extends CrudRepository<ExistingWildcardProjectionConflictEntity, String> {
}

@MongoWildcardIndex(name = 'existing_wildcard_projection_conflict_idx', wildcardProjection = '{ "metadata.internal": 0 }')
@MappedEntity('existing_wildcard_projection_conflict_entities')
class ExistingWildcardProjectionConflictEntity {
    @Id
    @GeneratedValue
    String id

    Map<String, Object> metadata
}

@MongoRepository
interface ExistingStorageEngineConflictEntityRepository extends CrudRepository<ExistingStorageEngineConflictEntity, String> {
}

@MappedEntity('existing_storage_engine_conflict_entities')
class ExistingStorageEngineConflictEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_storage_engine_conflict_idx', storageEngine = '{ "wiredTiger": {} }')
    String code
}
