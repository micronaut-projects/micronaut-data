package io.micronaut.data.azure.reactive


import io.micronaut.data.azure.NoPartitionKeyCosmosDbSpec
import io.micronaut.data.cosmos.config.CosmosDatabaseConfiguration
import io.micronaut.data.cosmos.config.StorageUpdatePolicy
import spock.lang.IgnoreIf


@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class NoPartitionKeyCosmosReactiveDbSpec extends NoPartitionKeyCosmosDbSpec implements CosmosReactiveConfigured {

    def "test configuration"() {
        given:
            def config = context.getBean(CosmosDatabaseConfiguration)

        expect:
            config.databaseName == 'mydb'
            config.reactive
            config.throughput.autoScale
            config.throughput.requestUnits == 1000
            config.updatePolicy == StorageUpdatePolicy.CREATE_IF_NOT_EXISTS
            !config.containers || config.containers.size() == 0
    }


}
