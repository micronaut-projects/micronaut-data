package io.micronaut.data.document.mongodb.validation.reactiveindexfailurepolicy

import io.micronaut.data.document.mongodb.validation.indexfailurepolicy.MongoIndexFailurePolicyWarnContinueSpec

class MongoReactiveIndexFailurePolicyWarnContinueSpec extends MongoIndexFailurePolicyWarnContinueSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
