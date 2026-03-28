package io.micronaut.data.document.mongodb.reactive

import io.micronaut.data.document.mongodb.compound.MongoCompoundIndexCreationSpec

class MongoReactiveCompoundIndexCreationSpec extends MongoCompoundIndexCreationSpec {

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
