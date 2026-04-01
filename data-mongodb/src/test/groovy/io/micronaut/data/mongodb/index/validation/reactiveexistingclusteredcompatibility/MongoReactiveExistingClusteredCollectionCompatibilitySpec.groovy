package io.micronaut.data.mongodb.index.validation.reactiveexistingclusteredcompatibility

import io.micronaut.data.mongodb.index.validation.existingclusteredcompatibility.MongoExistingClusteredCollectionCompatibilitySpec

class MongoReactiveExistingClusteredCollectionCompatibilitySpec extends MongoExistingClusteredCollectionCompatibilitySpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
