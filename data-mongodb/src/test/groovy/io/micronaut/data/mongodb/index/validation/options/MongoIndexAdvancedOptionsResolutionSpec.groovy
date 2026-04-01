package io.micronaut.data.mongodb.index.validation.options

import com.mongodb.client.model.geojson.Point
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexType
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import io.micronaut.data.mongodb.annotation.index.MongoTextIndexed
import io.micronaut.data.mongodb.annotation.index.MongoWildcardIndex
import io.micronaut.data.mongodb.common.MongoEntityIndexes
import org.bson.Document
import spock.lang.Shared
import spock.lang.Specification

class MongoIndexAdvancedOptionsResolutionSpec extends Specification {

    @Shared
    Map<Class<?>, RuntimePersistentEntity<?>> entities = [:]

    void 'resolves comment for simple index'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(CommentSimpleEntity)).indexes
        def index = indexes.find { it.name() == 'comment_simple_idx' }

        then:
        index != null
        index.comment() == 'simple-comment'
        index.commitQuorum() == null
        Document.parse(index.storageEngine()) == Document.parse('{"wiredTiger":{}}')
    }

    void 'resolves comment and commitQuorum for compound index'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(CommentCommitQuorumCompoundEntity)).indexes
        def index = indexes.find { it.name() == 'comment_commit_compound_idx' }

        then:
        index != null
        index.comment() == 'compound-comment'
        index.commitQuorum() == 'majority'
        Document.parse(index.storageEngine()) == Document.parse('{"wiredTiger":{}}')
    }

    void 'resolves comment and commitQuorum for top-level wildcard index'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(CommentCommitQuorumWildcardEntity)).indexes
        def index = indexes.find { it.name() == 'comment_commit_wildcard_idx' }

        then:
        index != null
        index.comment() == 'wildcard-comment'
        index.commitQuorum() == 'majority'
        Document.parse(index.storageEngine()) == Document.parse('{"wiredTiger":{}}')
    }

    void 'fails when text indexed fields define different comments'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(InvalidTextCommentEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('must use the same comment option')
    }

    void 'fails when text indexed fields define different storageEngine options'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(InvalidTextStorageEngineEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('must use the same storageEngine option')
    }

    void 'resolves text default language, override, and text index version'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(TextLanguageOptionsEntity)).indexes
        def index = indexes.find { it.name() == 'text_lang_idx' }

        then:
        index != null
        index.defaultLanguage() == 'french'
        index.languageOverride() == 'lang'
        index.textIndexVersion() == 3
    }

    void 'fails when text indexed fields define different defaultLanguage options'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(InvalidTextDefaultLanguageEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('must use the same defaultLanguage option')
    }

    void 'resolves embedded field simple index path'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(EmbeddedFieldIndexedEntity)).indexes
        def index = indexes.find { it.name() == 'embedded_state_idx' }

        then:
        index != null
        index.fields().size() == 1
        index.fields()[0].path() == 'location.state'
        index.fields()[0].order() == 1
    }

    void 'resolves embedded field text index path'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(EmbeddedTextIndexedEntity)).indexes
        def index = indexes.find { it.name() == 'embedded_text_idx' }

        then:
        index != null
        index.fields()*.path().contains('details.city')
        index.fields().find { it.path() == 'details.city' }.weight() == 3
    }

    void 'resolves 2dsphere sphereVersion'() {
        when:
        def indexes = MongoEntityIndexes.create(getRuntimePersistentEntity(GeoSphereVersionEntity)).indexes
        def index = indexes.find { it.name() == 'geo_sphere_version_idx' }

        then:
        index != null
        index.sphereVersion() == 3
    }

    void 'fails when 2dsphere sphereVersion is used on non-2dsphere index type'() {
        when:
        MongoEntityIndexes.create(getRuntimePersistentEntity(InvalidGeoSphereVersionOn2dEntity))

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('2dsphere-specific geospatial options are only supported for Mongo 2dsphere indexes')
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

@MappedEntity('comment_simple_entity')
class CommentSimpleEntity {
    @MongoIndexed(name = 'comment_simple_idx', comment = 'simple-comment', storageEngine = '{ "wiredTiger": {} }')
    String name
}

@MongoCompoundIndex(
        name = 'comment_commit_compound_idx',
        fields = [
                @MongoCompoundIndexField('name'),
                @MongoCompoundIndexField('city')
        ],
        comment = 'compound-comment',
        commitQuorum = 'majority',
        storageEngine = '{ "wiredTiger": {} }'
)
@MappedEntity('comment_commit_compound_entity')
class CommentCommitQuorumCompoundEntity {
    String name
    String city
}

@MongoWildcardIndex(name = 'comment_commit_wildcard_idx', comment = 'wildcard-comment', commitQuorum = 'majority', storageEngine = '{ "wiredTiger": {} }')
@MappedEntity('comment_commit_wildcard_entity')
class CommentCommitQuorumWildcardEntity {
    String name
}

@MappedEntity('invalid_text_comment_entity')
class InvalidTextCommentEntity {
    @MongoTextIndexed(comment = 'a')
    String first

    @MongoTextIndexed(comment = 'b')
    String second
}

@MappedEntity('invalid_text_storage_engine_entity')
class InvalidTextStorageEngineEntity {
    @MongoTextIndexed(storageEngine = '{ "wiredTiger": {} }')
    String first

    @MongoTextIndexed(storageEngine = '{ "wiredTiger": { "configString": "block_compressor=zlib" } }')
    String second
}

@MappedEntity('text_language_options_entity')
class TextLanguageOptionsEntity {
    @MongoTextIndexed(name = 'text_lang_idx', weight = 2, defaultLanguage = 'french', languageOverride = 'lang', textIndexVersion = 3)
    String title

    @MongoTextIndexed(name = 'text_lang_idx', weight = 5, defaultLanguage = 'french', languageOverride = 'lang', textIndexVersion = 3)
    String description
}

@MappedEntity('invalid_text_default_language_entity')
class InvalidTextDefaultLanguageEntity {
    @MongoTextIndexed(defaultLanguage = 'english')
    String first

    @MongoTextIndexed(defaultLanguage = 'spanish')
    String second
}

@MappedEntity('geo_sphere_version_entity')
class GeoSphereVersionEntity {
    @MongoGeoIndexed(name = 'geo_sphere_version_idx', sphereVersion = 3)
    Point location
}

@MappedEntity('invalid_geo_sphere_version_on_2d_entity')
class InvalidGeoSphereVersionOn2dEntity {
    @MongoGeoIndexed(name = 'invalid_geo_sphere_version_idx', type = MongoGeoIndexType.GEO_2D, sphereVersion = 3)
    Map<String, Object> location
}

@MappedEntity('embedded_field_indexed_entity')
class EmbeddedFieldIndexedEntity {
    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedFieldIndexedLocation location
}

@Embeddable
class EmbeddedFieldIndexedLocation {
    @MongoIndexed(name = 'embedded_state_idx')
    String state

    String city
}

@MappedEntity('embedded_text_indexed_entity')
class EmbeddedTextIndexedEntity {
    @Relation(Relation.Kind.EMBEDDED)
    EmbeddedTextIndexedDetails details
}

@Embeddable
class EmbeddedTextIndexedDetails {
    String title

    @MongoTextIndexed(name = 'embedded_text_idx', weight = 3)
    String city
}
