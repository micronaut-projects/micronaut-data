package io.micronaut.data.document.mongodb.validation.reactivetextversionconflict

import io.micronaut.data.document.mongodb.validation.textversionconflict.MongoTextIndexVersionConflictSpec

class MongoReactiveTextIndexVersionConflictSpec extends MongoTextIndexVersionConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
