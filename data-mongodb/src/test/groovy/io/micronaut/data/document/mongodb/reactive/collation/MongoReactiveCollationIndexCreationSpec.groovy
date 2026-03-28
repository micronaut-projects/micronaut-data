package io.micronaut.data.document.mongodb.reactive.collation

import io.micronaut.data.document.mongodb.collation.MongoCollationIndexCreationSpec

class MongoReactiveCollationIndexCreationSpec extends MongoCollationIndexCreationSpec {

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
