package io.micronaut.data.document.mongodb.reactive.wildcard.toplevel

import io.micronaut.data.document.mongodb.wildcard.toplevel.MongoTopLevelWildcardIndexCreationSpec

class MongoReactiveTopLevelWildcardIndexCreationSpec extends MongoTopLevelWildcardIndexCreationSpec {

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
