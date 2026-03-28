package io.micronaut.data.document.mongodb.reactive.wildcard.toplevel

import io.micronaut.data.document.mongodb.wildcard.toplevel.MongoTopLevelWildcardProjectionIndexCreationSpec

class MongoReactiveTopLevelWildcardProjectionIndexCreationSpec extends MongoTopLevelWildcardProjectionIndexCreationSpec {

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
