package io.micronaut.data.mongodb.index.validation.existingindexcompatibility

import com.mongodb.client.MongoClient
import com.mongodb.client.model.geojson.Point
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndex
import io.micronaut.data.mongodb.annotation.index.MongoCompoundIndexField
import io.micronaut.data.mongodb.annotation.index.MongoGeoIndexed
import io.micronaut.data.mongodb.annotation.index.MongoIndexDirection
import io.micronaut.data.mongodb.annotation.index.MongoIndexed
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import org.bson.Document
import spock.lang.Specification

class MongoExistingIndexCompatibilitySpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.existingindexcompatibility']
    }

    void 'starts successfully when matching simple index already exists'() {
        given:
        prepareExistingIndex('existing_simple_index_entities',
                new Document('key', new Document('name', 1))
                        .append('name', 'existing_name_idx')
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

    void 'starts successfully when matching compound index already exists'() {
        given:
        prepareExistingIndex('existing_compound_index_entities',
                new Document('key', new Document('name', 1).append('age', -1))
                        .append('name', 'existing_name_age_idx')
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

    void 'starts successfully when matching 2dsphere index already exists'() {
        given:
        prepareExistingIndex('existing_geo_index_entities',
                new Document('key', new Document('location', '2dsphere'))
                        .append('name', 'existing_geo_location_idx')
                        .append('2dsphereIndexVersion', 3)
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

    void 'starts successfully when matching compound text index already exists'() {
        given:
        prepareExistingIndex('existing_compound_text_index_entities',
                new Document('key', new Document('tenant_id', 1).append('_fts', 'text').append('_ftsx', 1).append('created_at', -1))
                        .append('name', 'existing_compound_text_idx')
                        .append('weights', new Document('title', 2).append('description', 5))
                        .append('default_language', 'english')
                        .append('language_override', 'docLang')
                        .append('textIndexVersion', 3)
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
        MongoClient mongoClient = preContext.getBean(MongoClient)
        def database = mongoClient.getDatabase('test')
        def collection = database.getCollection(collectionName)
        collection.drop()
        database.runCommand(new Document('createIndexes', collectionName)
                .append('indexes', [index]))

        def existing = collection.listIndexes().find { it.getString('name') == index.getString('name') }
        assert existing != null
        preContext.close()
    }
}

@MappedEntity('existing_simple_index_entities')
class ExistingSimpleIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoIndexed(name = 'existing_name_idx', unique = true)
    String name
}

@MongoRepository
interface ExistingCompoundIndexEntityRepository extends CrudRepository<ExistingCompoundIndexEntity, String> {
}

@MongoCompoundIndex(
        name = 'existing_name_age_idx',
        unique = true,
        fields = [
                @MongoCompoundIndexField(value = 'name', direction = MongoIndexDirection.ASC),
                @MongoCompoundIndexField(value = 'age', direction = MongoIndexDirection.DESC)
        ]
)
@MappedEntity('existing_compound_index_entities')
class ExistingCompoundIndexEntity {
    @Id
    @GeneratedValue
    String id

    String name

    Integer age
}

@MongoRepository
interface ExistingGeoIndexEntityRepository extends CrudRepository<ExistingGeoIndexEntity, String> {
}

@MappedEntity('existing_geo_index_entities')
class ExistingGeoIndexEntity {
    @Id
    @GeneratedValue
    String id

    @MongoGeoIndexed(name = 'existing_geo_location_idx', sphereVersion = 3)
    Point location
}

@MongoRepository
interface ExistingCompoundTextIndexEntityRepository extends CrudRepository<ExistingCompoundTextIndexEntity, String> {
}

@MongoCompoundIndex(
        name = 'existing_compound_text_idx',
        defaultLanguage = 'english',
        languageOverride = 'docLang',
        textIndexVersion = 3,
        fields = [
                @MongoCompoundIndexField(value = 'tenantId', direction = MongoIndexDirection.ASC),
                @MongoCompoundIndexField(value = 'title', text = true, weight = 2),
                @MongoCompoundIndexField(value = 'description', text = true, weight = 5),
                @MongoCompoundIndexField(value = 'createdAt', direction = MongoIndexDirection.DESC)
        ]
)
@MappedEntity('existing_compound_text_index_entities')
class ExistingCompoundTextIndexEntity {
    @Id
    @GeneratedValue
    String id

    String tenantId

    String title

    String description

    Long createdAt
}
