package io.micronaut.data.azure


import com.azure.cosmos.models.PartitionKey
import io.micronaut.context.ApplicationContext
import io.micronaut.data.azure.entities.nopartitionkey.NoPartitionKeyEntity
import io.micronaut.data.azure.repositories.NoPartitionKeyEntityRepository
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

/**
 * This tests operations with an entity that does not have partition key defined.
 */
@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class NoPartitionKeyCosmosDbSpec extends Specification implements AzureCosmosTestProperties {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    NoPartitionKeyEntityRepository repository = context.getBean(NoPartitionKeyEntityRepository)

    @Override
    Map<String, String> getDbInitProperties() {
        return [
                'azure.cosmos.database.packages'      : 'io.micronaut.data.azure.entities.nopartitionkey',
                'azure.cosmos.database.update-policy' : 'CREATE_IF_NOT_EXISTS'
        ]
    }

    def "test CRUD against container with no partition key"() {
        given:
            def entity1 = new NoPartitionKeyEntity()
            entity1.id = "1"
            entity1.name = "Entity1"
            entity1.grade = 2
            entity1.customName = "CustomEnt2"
            repository.save(entity1)
            def entity2 = new NoPartitionKeyEntity()
            entity2.id = "2"
            entity2.name = "Entity2"
            entity2.grade = 4
            entity2.tags = Arrays.asList("entity2", "grade4", "id2", "custom2").toArray()
            entity2.rating = 3.5
            repository.save(entity2)
        when:
            def maxGrade = repository.findMaxGradeByIdIn(Arrays.asList(entity1.id, entity2.id))
            def sumGrade = repository.findSumGrade()
            def avgGrade = repository.findAvgGradeByNameIn(Arrays.asList(entity1.name, entity2.name))
            def minGrade = repository.findMinGrade()
        then:
            maxGrade == 4
            sumGrade == 6
            avgGrade == 3
            minGrade == 2
        when:
            def name = repository.findNameById(entity1.id)
            def tags1 = repository.getTagsById(entity1.id)
            def tags2 = repository.getTagsById(entity2.id)
            def notEmptyNameEntities = repository.findByNameIsNotEmpty();
            def emptyCustomNameEntities = repository.findByCustomNameIsEmpty();
            def nullRatingEntities = repository.findByRatingIsNull();
            def nullCustomNameEntities = repository.findByCustomNameIsNull();
            def notNullRatingEntities = repository.findByRatingIsNotNull();
            def notNullCustomNameEntities = repository.findByCustomNameIsNotNull();
        then:
            name == entity1.name
            !tags1 || tags1.length == 0
            tags2.length == 4
            notEmptyNameEntities.size() == 2
            emptyCustomNameEntities.size() == 1
            emptyCustomNameEntities[0].id == entity2.id
            nullRatingEntities.size() == 1
            nullRatingEntities[0].id == entity1.id
            nullCustomNameEntities.size() == 1
            nullCustomNameEntities[0].id == entity2.id
            notNullRatingEntities.size() == 1
            notNullRatingEntities[0].id == entity2.id
            notNullCustomNameEntities.size() == 1
            notNullCustomNameEntities[0].id == entity1.id
        when:
            def entities = repository.findByTagsArrayContains("custom2")
        then:
            entities.size() == 1
            entities[0].id == entity2.id
        when:
            entities = repository.findByGradeIn(Arrays.asList(entity2.grade))
        then:
            entities.size() == 1
            entities[0].id == entity2.id
        when:
            def optEntity1 = repository.findById(entity1.id)
            def optEntity2 = repository.findById(entity2.id)
        then:
            optEntity1.present
            optEntity2.present
        when:
            repository.updateGrade(entity1.id, 10)
            optEntity1 = repository.findById(entity1.id)
        then:
            optEntity1.get().grade == 10
        when:"Using non matching partition key won't update"
            repository.updateGrade(entity1.id, 20, new PartitionKey(1))
            optEntity1 = repository.findById(entity1.id)
        then:
            optEntity1.get().grade == 10
        when:
            repository.deleteByName(entity1.name)
            optEntity1 = repository.findById(entity1.id)
        then:
            !optEntity1.present
        when:"Using non matching partition key won't delete"
            repository.deleteByGrade(entity2.grade, new PartitionKey(2))
            optEntity2 = repository.findById(entity2.id)
        then:
            optEntity2.present
        when:
            entities = repository.findAllByName(optEntity2.get().name)
        then:
            entities.size() > 0
            def foundEntity2 = entities.stream().filter(i -> i.id == entity2.id).findFirst()
            foundEntity2.present
            foundEntity2.get().id == entity2.id
        when:
            entities = repository.findAllByNameAndGrade(optEntity2.get().name, optEntity2.get().grade)
        then:
            entities.size() > 0
        when:
            entities = repository.findAllByNameAndGrade(UUID.randomUUID().toString(), optEntity2.get().grade)
        then:
            entities.size() == 0
        cleanup:
            repository.deleteAll()
    }

    def "test configuration"() {
        given:
            def config = context.getBean(CosmosDatabaseConfiguration)

        expect:
            config.databaseName == 'mydb'
            config.throughput.autoScale
            config.throughput.requestUnits == 1000
            config.updatePolicy == StorageUpdatePolicy.CREATE_IF_NOT_EXISTS
            !config.containers || config.containers.size() == 0
    }


}
