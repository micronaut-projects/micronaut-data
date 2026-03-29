package io.micronaut.data.document.mongodb.validation.existingindexname

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

class MongoExistingIndexNameReconciliationSpec extends Specification implements MongoTestPropertyProvider {

    private static final List<String> NAME_COLLECTIONS = [
            'existing_name_conflict_index_entities',
            'existing_name_reconciliation_index_entities'
    ]

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.existingindexname']
    }

    void 'fails fast when existing index name differs for same key'() {
        given:
        prepareExistingIndex('existing_name_conflict_index_entities',
                new Document('key', new Document('email', 1))
                        .append('name', 'existing_name_idx')
                        .append('unique', true)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertNameConflict(e, 'existing_name_conflict_entities_idx', 'existing_name_idx')
    }

    void 'starts successfully when desired index is unnamed and existing index has name for same key'() {
        given:
        prepareExistingIndex('existing_name_reconciliation_index_entities',
                new Document('key', new Document('username', 1))
                        .append('name', 'existing_named_username_idx')
                        .append('unique', true)
        )

        ApplicationContext startupContext = null

        when:
        startupContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        noExceptionThrown()

        cleanup:
        startupContext?.close()
    }

    protected void prepareExistingIndex(String collectionName, Document index) {
        ApplicationContext preContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'false'
        ])
        try {
            MongoClient mongoClient = preContext.getBean(MongoClient)
            def database = mongoClient.getDatabase('test')
            NAME_COLLECTIONS.each { database.getCollection(it).drop() }
            def collection = database.getCollection(collectionName)
            database.runCommand(new Document('createIndexes', collectionName)
                    .append('indexes', [index]))

            def existing = collection.listIndexes().find { it.getString('name') == index.getString('name') }
            assert existing != null
        } finally {
            preContext.close()
        }
    }

    protected static void assertNameConflict(RuntimeException e, String desiredName, String existingName) {
        assert e.message != null
        assert e.message.contains('Conflicting existing MongoDB index name')
        assert e.message.contains('desired MongoResolvedIndex')
        assert e.message.contains(desiredName)
        assert e.message.contains(existingName)
    }
}

@MongoRepository
interface ExistingNameConflictEntityRepository extends CrudRepository<ExistingNameConflictEntity, String> {
}

@MappedEntity('existing_name_conflict_index_entities')
class ExistingNameConflictEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_name_conflict_entities_idx', unique = true)
    String email
}

@MongoRepository
interface ExistingNameReconciliationEntityRepository extends CrudRepository<ExistingNameReconciliationEntity, String> {
}

@MappedEntity('existing_name_reconciliation_index_entities')
class ExistingNameReconciliationEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(unique = true)
    String username
}
