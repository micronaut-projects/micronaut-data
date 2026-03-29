package io.micronaut.data.document.mongodb.validation.reactiveexistingindexconflict

import io.micronaut.data.document.mongodb.validation.existingindexconflict.MongoExistingIndexConflictSpec

class MongoReactiveExistingIndexConflictSpec extends MongoExistingIndexConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
