package io.micronaut.data.mongodb.index.validation.reactivetextversionconflict

import io.micronaut.data.mongodb.index.validation.textversionconflict.MongoTextIndexVersionConflictSpec

class MongoReactiveTextIndexVersionConflictSpec extends MongoTextIndexVersionConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
