package io.micronaut.data.document.mongodb.validation.reactiveexistingindexadvancedconflict

import io.micronaut.data.document.mongodb.validation.existingindexadvancedconflict.MongoExistingIndexAdvancedConflictSpec

class MongoReactiveExistingIndexAdvancedConflictSpec extends MongoExistingIndexAdvancedConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
