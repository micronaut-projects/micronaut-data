package io.micronaut.data.mongodb.index.validation.reactiveexistingindexadvancedconflict

import io.micronaut.data.mongodb.index.validation.existingindexadvancedconflict.MongoExistingIndexAdvancedConflictSpec

class MongoReactiveExistingIndexAdvancedConflictSpec extends MongoExistingIndexAdvancedConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
