package io.micronaut.data.mongodb.index.validation.reactiveexistingindexconflict

import io.micronaut.data.mongodb.index.validation.existingindexconflict.MongoExistingIndexConflictSpec

class MongoReactiveExistingIndexConflictSpec extends MongoExistingIndexConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
