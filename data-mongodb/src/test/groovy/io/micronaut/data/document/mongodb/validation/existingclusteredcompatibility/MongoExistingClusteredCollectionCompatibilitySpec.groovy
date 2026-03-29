package io.micronaut.data.document.mongodb.validation.existingclusteredcompatibility

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoClusteredIndex
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.Document
import spock.lang.Specification

class MongoExistingClusteredCollectionCompatibilitySpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.existingclusteredcompatibility']
    }

    void 'starts successfully when matching clustered collection options already exist'() {
        given:
        prepareExistingClusteredCollection('existing_clustered_compatibility_entities',
                new Document('key', new Document('_id', 1))
                        .append('name', 'existing_clustered_compatibility_idx')
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

    protected void prepareExistingClusteredCollection(String collectionName, Document clusteredIndex) {
        ApplicationContext preContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'false',
                'micronaut.data.mongodb.driver-type'  : 'sync',
                'mongodb.package-names'               : 'io.micronaut.data.document.mongodb._none_'
        ])
        try {
            MongoClient mongoClient = preContext.getBean(MongoClient)
            def database = mongoClient.getDatabase('test')
            database.getCollection(collectionName).drop()
            database.runCommand(new Document('create', collectionName)
                    .append('clusteredIndex', clusteredIndex))

            def existingCollection = database.listCollections().into([]).find { it.getString('name') == collectionName }
            assert existingCollection != null
            def options = existingCollection.get('options', Document)
            assert options != null
            assert options.get('clusteredIndex', Document) != null
        } finally {
            preContext.close()
        }
    }
}

@MongoRepository
interface ExistingClusteredCompatibilityEntityRepository extends CrudRepository<ExistingClusteredCompatibilityEntity, java.time.Instant> {
}

@MongoClusteredIndex(name = 'existing_clustered_compatibility_idx')
@MappedEntity('existing_clustered_compatibility_entities')
class ExistingClusteredCompatibilityEntity {
    @Id
    java.time.Instant id

    String name
}
