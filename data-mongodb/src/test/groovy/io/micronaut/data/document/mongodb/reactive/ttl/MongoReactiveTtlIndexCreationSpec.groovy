package io.micronaut.data.document.mongodb.reactive.ttl

import io.micronaut.data.document.mongodb.ttl.MongoTtlIndexCreationSpec

class MongoReactiveTtlIndexCreationSpec extends MongoTtlIndexCreationSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }

    @Override
    Class<?> expectedCollectionsCreatorBeanType() {
        io.micronaut.data.mongodb.init.MongoReactiveCollectionsCreator
    }
}
