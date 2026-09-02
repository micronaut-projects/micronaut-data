package io.micronaut.data.mongodb.index.reactive.ttl

import io.micronaut.data.mongodb.index.ttl.MongoTtlIndexCreationSpec

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
