package io.micronaut.data.aws.dynamodb

import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

trait AwsDynamoDbTestProperties implements TestPropertyProvider {

    @Shared
    @AutoCleanup("stop")
    GenericContainer dynamoDb = new GenericContainer("amazon/dynamodb-local:latest")
            .withExposedPorts(8000)

    @Override
    Map<String, String> getProperties() {
        dynamoDb.start()
        return [
                'aws.dynamodb.endpoint'              : 'http://localhost:' + dynamoDb.getFirstMappedPort(),
                'aws.dynamodb.region'                : 'us-east-1',
                'aws.dynamodb.aws-access-key-id'     : 'any',
                'aws.dynamodb.aws-secret-access-key' : 'any'
        ]
    }

    def cleanupSpec() {
        dynamoDb?.close()
    }
}
