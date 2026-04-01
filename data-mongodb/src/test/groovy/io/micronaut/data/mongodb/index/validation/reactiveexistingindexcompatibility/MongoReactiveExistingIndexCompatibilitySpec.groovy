package io.micronaut.data.mongodb.index.validation.reactiveexistingindexcompatibility

import io.micronaut.data.mongodb.index.validation.existingindexcompatibility.MongoExistingIndexCompatibilitySpec

class MongoReactiveExistingIndexCompatibilitySpec extends MongoExistingIndexCompatibilitySpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
