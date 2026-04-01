package io.micronaut.data.mongodb.index.validation.reactiveexistingclusteredconflict

import io.micronaut.data.mongodb.index.validation.existingclusteredconflict.MongoExistingClusteredCollectionConflictSpec

class MongoReactiveExistingClusteredCollectionConflictSpec extends MongoExistingClusteredCollectionConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
