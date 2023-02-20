package io.micronaut.data.aws.dynamodb

import io.micronaut.data.aws.dynamodb.common.DynamoDbEntity
import io.micronaut.data.aws.dynamodb.entities.Device
import io.micronaut.data.aws.dynamodb.utils.TableUtils
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class DynamoDbEntitySpec extends Specification {

    @Inject
    private RuntimeEntityRegistry runtimeEntityRegistry

    void 'initialize entity from mapping class'() {
        given:
        def entities = TableUtils.findMappedEntities(runtimeEntityRegistry, List.of("io.micronaut.data.aws.dynamodb.entities"), Device.class.name)
        entities.size() == 1
        def entity = entities[0]
        when:
        entity
        def dynamoEntity = DynamoDbEntity.get(entity)
        then:
        dynamoEntity
        !dynamoEntity.versionField
        dynamoEntity.sortKey == "product"
        dynamoEntity.partitionKey == "vendorId"
        dynamoEntity.getIndexesByField("description").empty
        def countryIndexes = dynamoEntity.getIndexesByField("country")
        countryIndexes.size() == 1
        def countryIndex = countryIndexes[0]
        countryIndex.fieldName == "country"
        countryIndex.indexFieldRole == DynamoDbEntity.IndexFieldRole.PARTITION_KEY
        countryIndex.indexName == "CountryRegionIndex"
        def regionIndexes = dynamoEntity.getIndexesByField("region")
        regionIndexes.size() == 1
        def regionIndex = regionIndexes[0]
        regionIndex.fieldName == "region"
        regionIndex.indexFieldRole == DynamoDbEntity.IndexFieldRole.SORT_KEY
        regionIndex.indexName == "CountryRegionIndex"
    }
}
