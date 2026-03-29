package io.micronaut.data.document.mongodb.validation.reactiveexistingclusteredcompatibility

import io.micronaut.data.document.mongodb.validation.existingclusteredcompatibility.MongoExistingClusteredCollectionCompatibilitySpec

class MongoReactiveExistingClusteredCollectionCompatibilitySpec extends MongoExistingClusteredCollectionCompatibilitySpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
