package io.micronaut.data.mongodb.index.reactive

import io.micronaut.data.mongodb.index.simple.MongoIndexCreationSpec

class MongoReactiveIndexCreationSpec extends MongoIndexCreationSpec {

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
