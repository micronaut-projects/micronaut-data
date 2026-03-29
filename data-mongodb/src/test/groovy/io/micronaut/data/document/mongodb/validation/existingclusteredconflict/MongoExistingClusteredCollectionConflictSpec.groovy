package io.micronaut.data.document.mongodb.validation.existingclusteredconflict

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

class MongoExistingClusteredCollectionConflictSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.existingclusteredconflict']
    }

    void 'fails fast when existing clustered collection options conflict'() {
        given:
        prepareExistingClusteredCollection('existing_clustered_conflict_entities',
                new Document('key', new Document('_id', 1))
                        .append('name', 'different_clustered_name')
                        .append('unique', true)
        )

        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-indexes': 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        assert e.message != null
        assert e.message.contains('Conflicting existing MongoDB collection options')
        assert e.message.contains('existing_clustered_conflict_entities')
        assert e.message.contains('clusteredIndexName')
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
        } finally {
            preContext.close()
        }
    }
}

@MongoRepository
interface ExistingClusteredConflictEntityRepository extends CrudRepository<ExistingClusteredConflictEntity, java.time.Instant> {
}

@MongoClusteredIndex(name = 'expected_clustered_name')
@MappedEntity('existing_clustered_conflict_entities')
class ExistingClusteredConflictEntity {
    @Id
    java.time.Instant id

    String name
}
