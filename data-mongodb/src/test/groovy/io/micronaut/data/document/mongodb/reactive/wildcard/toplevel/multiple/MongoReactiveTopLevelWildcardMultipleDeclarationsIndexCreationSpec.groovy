package io.micronaut.data.document.mongodb.reactive.wildcard.toplevel.multiple

import io.micronaut.data.document.mongodb.wildcard.toplevel.multiple.MongoTopLevelWildcardMultipleDeclarationsIndexCreationSpec

class MongoReactiveTopLevelWildcardMultipleDeclarationsIndexCreationSpec extends MongoTopLevelWildcardMultipleDeclarationsIndexCreationSpec {

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
