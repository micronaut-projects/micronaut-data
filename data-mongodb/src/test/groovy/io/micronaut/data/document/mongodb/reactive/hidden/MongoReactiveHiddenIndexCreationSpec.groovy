package io.micronaut.data.document.mongodb.reactive.hidden

import io.micronaut.data.document.mongodb.hidden.MongoHiddenIndexCreationSpec

class MongoReactiveHiddenIndexCreationSpec extends MongoHiddenIndexCreationSpec {

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
