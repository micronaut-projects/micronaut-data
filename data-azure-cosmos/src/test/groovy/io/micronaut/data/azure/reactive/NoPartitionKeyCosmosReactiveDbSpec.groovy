package io.micronaut.data.azure.reactive


import io.micronaut.data.azure.NoPartitionKeyCosmosDbSpec
import spock.lang.IgnoreIf


@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class NoPartitionKeyCosmosReactiveDbSpec extends NoPartitionKeyCosmosDbSpec implements CosmosReactiveConfigured {
    @Override
    boolean expectedReactiveConfigValue() {
        true
    }

    @Override
    Map<String, String> getDbInitProperties() {
        return [
                'azure.cosmos.database.packages'      : 'io.micronaut.data.azure.entities.nopartitionkey',
                'azure.cosmos.database.update-policy' : 'CREATE_IF_NOT_EXISTS'
        ]
    }
}
