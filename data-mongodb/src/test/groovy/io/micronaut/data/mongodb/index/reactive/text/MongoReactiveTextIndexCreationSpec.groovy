package io.micronaut.data.mongodb.index.reactive.text

import io.micronaut.data.mongodb.index.text.MongoTextIndexCreationSpec

class MongoReactiveTextIndexCreationSpec extends MongoTextIndexCreationSpec {

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
