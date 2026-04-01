package io.micronaut.data.mongodb.index.validation.reactiveindexfailurepolicy

import io.micronaut.data.mongodb.index.validation.indexfailurepolicy.MongoIndexFailurePolicyWarnContinueSpec

class MongoReactiveIndexFailurePolicyWarnContinueSpec extends MongoIndexFailurePolicyWarnContinueSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
