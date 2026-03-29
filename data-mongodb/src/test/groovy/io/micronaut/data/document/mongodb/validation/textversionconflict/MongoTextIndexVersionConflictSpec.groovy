package io.micronaut.data.document.mongodb.validation.textversionconflict

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.MongoTextIndexed
import io.micronaut.data.repository.CrudRepository
import org.bson.Document
import spock.lang.Specification

class MongoTextIndexVersionConflictSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.textversionconflict']
    }

    void 'fails fast when existing text index has conflicting textIndexVersion'() {
        given:
        ApplicationContext preContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'false'
        ])
        MongoClient mongoClient = preContext.getBean(MongoClient)
        def database = mongoClient.getDatabase('test')
        def collection = database.getCollection('text_index_version_conflict_entities')
        collection.drop()
        database.runCommand(new Document('createIndexes', 'text_index_version_conflict_entities')
                .append('indexes', [new Document('key', new Document('title', 'text'))
                        .append('name', 'text_version_idx')
                        .append('default_language', 'spanish')
                        .append('textIndexVersion', 2)]))

        def existing = collection.listIndexes().find { it.getString('name') == 'text_version_idx' }
        assert existing != null
        assert existing.getInteger('textIndexVersion') == 2
        preContext.close()

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assertManagedConflict(e, 'text_version_idx', 'textIndexVersion')
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
interface TextIndexVersionConflictEntityRepository extends CrudRepository<TextIndexVersionConflictEntity, String> {
}

@MappedEntity('text_index_version_conflict_entities')
class TextIndexVersionConflictEntity {
    @Id
    @GeneratedValue
    String id

    @MongoTextIndexed(name = 'text_version_idx', defaultLanguage = 'spanish', textIndexVersion = 3)
    String title
}
