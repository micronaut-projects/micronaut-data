package io.micronaut.data.aws.dynamodb

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class AwsDynamoDbFactorySpec extends Specification implements AwsDynamoDbTestProperties {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    void 'verify aws dynamo db factory'() {
        when:
        def dynamoDb = context.getBean(AmazonDynamoDB)
        then:
        noExceptionThrown()
        dynamoDb
        when:
        def listTableResult = dynamoDb.listTables()
        then:
        listTableResult.getTableNames().empty
    }
}
