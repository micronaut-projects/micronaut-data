package io.micronaut.data.mongodb.index.database

import com.mongodb.client.MongoClient
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoIndexInspector
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MongoRepositoryDatabaseIndexCreationSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext

    @Shared
    MongoClient mongoClient

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.database']
    }

    def setupSpec() {
        applicationContext = ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])
        mongoClient = applicationContext.getBean(MongoClient)
    }

    void 'creates indexes in repository selected database'() {
        when:
        def indexes = MongoIndexInspector.listNormalizedIndexes(mongoClient, 'party', 'repository_database_index_entities')

        then:
        indexes*.name.contains('repository_database_name_idx')
    }
}

@MongoRepository(databaseName = 'party')
interface RepositoryDatabaseIndexEntityRepository extends CrudRepository<RepositoryDatabaseIndexEntity, String> {
}

@MappedEntity('repository_database_index_entities')
class RepositoryDatabaseIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'repository_database_name_idx')
    String name
}
