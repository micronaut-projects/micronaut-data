package io.micronaut.data.azure.reactive

import io.micronaut.data.azure.AzureCosmosTestProperties

trait CosmosReactiveConfigured implements AzureCosmosTestProperties {

    @Override
    Map<String, String> getProperties() {
        return super.getProperties() + [
                "azure.cosmos.database.reactive": 'true'
        ]
    }

}
