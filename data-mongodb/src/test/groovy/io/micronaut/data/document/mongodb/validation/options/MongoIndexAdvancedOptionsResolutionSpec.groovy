package io.micronaut.data.document.mongodb.validation.options

import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.mongodb.annotation.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoTextIndexed
import io.micronaut.data.mongodb.annotation.MongoWildcardIndex
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
