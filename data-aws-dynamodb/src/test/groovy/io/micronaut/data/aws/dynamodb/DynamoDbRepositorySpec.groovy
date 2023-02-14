package io.micronaut.data.aws.dynamodb

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import io.micronaut.context.ApplicationContext
import io.micronaut.data.aws.dynamodb.common.DynamoDbEntity
import io.micronaut.data.aws.dynamodb.entities.Device
import io.micronaut.data.aws.dynamodb.entities.DeviceId
import io.micronaut.data.aws.dynamodb.repositories.DeviceRepository
import io.micronaut.data.aws.dynamodb.utils.TableUtils
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class DynamoDbRepositorySpec extends Specification implements AwsDynamoDbTestProperties {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    AmazonDynamoDB amazonDynamoDB = context.getBean(AmazonDynamoDB)

    RuntimeEntityRegistry runtimeEntityRegistry = context.getBean(RuntimeEntityRegistry)

    DeviceRepository deviceRepository = context.getBean(DeviceRepository)

    void 'test create table'() {
        given:
        def entities = TableUtils.findMappedEntities(runtimeEntityRegistry, List.of("io.micronaut.data.aws.dynamodb.entities"), Device.class.name)
        entities.size() == 1
        def entity = entities[0]
        when:
        def dynamoEntity = DynamoDbEntity.get(entity)
        def createdTable = TableUtils.createTable(amazonDynamoDB, entity, dynamoEntity)
        then:
        createdTable

        when:
        def device = new Device()
        def id = new DeviceId()
        id.vendorId = 1L
        id.product = "Sharpener"
        device.id = id
        device.description = "Wet Stone Sharpener"
        device.country = "Serbia"
        device.region = "Belgrade"
        def result = TableUtils.insertEntity(amazonDynamoDB, entity, dynamoEntity, device)
        then:
        result
        when:
        def optDevice = deviceRepository.findById(id)
        then:
        !optDevice.present
    }
}
