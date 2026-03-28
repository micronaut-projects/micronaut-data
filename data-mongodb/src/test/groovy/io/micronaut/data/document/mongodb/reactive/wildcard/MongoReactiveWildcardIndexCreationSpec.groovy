package io.micronaut.data.document.mongodb.reactive.wildcard

import io.micronaut.data.document.mongodb.wildcard.MongoWildcardIndexCreationSpec

class MongoReactiveWildcardIndexCreationSpec extends MongoWildcardIndexCreationSpec {

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
