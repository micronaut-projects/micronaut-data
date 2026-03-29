package io.micronaut.data.document.mongodb.validation.existingindexconflict

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.Document
import spock.lang.Specification

class MongoExistingIndexConflictSpec extends Specification implements MongoTestPropertyProvider {

    private static final List<String> CONFLICT_COLLECTIONS = [
            'existing_unique_conflict_index_entities',
            'existing_collation_conflict_index_entities',
            'existing_partial_filter_conflict_index_entities'
    ]

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.existingindexconflict']
    }

    void 'fails fast when existing index has conflicting unique option'() {
        given:
        prepareExistingIndex('existing_unique_conflict_index_entities',
                new Document('key', new Document('email', 1))
                        .append('name', 'existing_unique_conflict_idx')
                        .append('unique', false)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_unique_conflict_idx', 'unique')
    }

    void 'fails fast when existing index has conflicting collation option'() {
        given:
        prepareExistingIndex('existing_collation_conflict_index_entities',
                new Document('key', new Document('name', 1))
                        .append('name', 'existing_collation_conflict_idx')
                        .append('collation', new Document('locale', 'fr').append('strength', 2))
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_collation_conflict_idx', 'collation')
    }

    void 'fails fast when existing index has conflicting partialFilterExpression option'() {
        given:
        prepareExistingIndex('existing_partial_filter_conflict_index_entities',
                new Document('key', new Document('status', 1))
                        .append('name', 'existing_partial_filter_conflict_idx')
                        .append('partialFilterExpression', new Document('status', new Document('$eq', 'ARCHIVED')))
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'existing_partial_filter_conflict_idx', 'partialFilterExpression')
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
interface ExistingUniqueConflictIndexEntityRepository extends CrudRepository<ExistingUniqueConflictIndexEntity, String> {
}

@MappedEntity('existing_unique_conflict_index_entities')
class ExistingUniqueConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_unique_conflict_idx', unique = true)
    String email
}

@MongoRepository
interface ExistingCollationConflictIndexEntityRepository extends CrudRepository<ExistingCollationConflictIndexEntity, String> {
}

@MappedEntity('existing_collation_conflict_index_entities')
class ExistingCollationConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_collation_conflict_idx', collation = '{ "locale": "en", "strength": 2 }')
    String name
}

@MongoRepository
interface ExistingPartialFilterConflictIndexEntityRepository extends CrudRepository<ExistingPartialFilterConflictIndexEntity, String> {
}

@MappedEntity('existing_partial_filter_conflict_index_entities')
class ExistingPartialFilterConflictIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_partial_filter_conflict_idx', partialFilterExpression = '{ "status": { "$eq": "ACTIVE" } }')
    String status
}
