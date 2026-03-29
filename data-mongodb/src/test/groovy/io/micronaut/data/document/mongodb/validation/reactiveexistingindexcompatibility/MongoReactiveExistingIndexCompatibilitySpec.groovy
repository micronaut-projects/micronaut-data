package io.micronaut.data.document.mongodb.validation.reactiveexistingindexcompatibility

import io.micronaut.data.document.mongodb.validation.existingindexcompatibility.MongoExistingIndexCompatibilitySpec

class MongoReactiveExistingIndexCompatibilitySpec extends MongoExistingIndexCompatibilitySpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
