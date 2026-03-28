package io.micronaut.data.document.mongodb.reactive.hashed

import io.micronaut.data.document.mongodb.hashed.MongoHashedIndexCreationSpec

class MongoReactiveHashedIndexCreationSpec extends MongoHashedIndexCreationSpec {

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
