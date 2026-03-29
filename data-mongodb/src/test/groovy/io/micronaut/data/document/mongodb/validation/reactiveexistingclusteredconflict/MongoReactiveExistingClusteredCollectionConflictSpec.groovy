package io.micronaut.data.document.mongodb.validation.reactiveexistingclusteredconflict

import io.micronaut.data.document.mongodb.validation.existingclusteredconflict.MongoExistingClusteredCollectionConflictSpec

class MongoReactiveExistingClusteredCollectionConflictSpec extends MongoExistingClusteredCollectionConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
