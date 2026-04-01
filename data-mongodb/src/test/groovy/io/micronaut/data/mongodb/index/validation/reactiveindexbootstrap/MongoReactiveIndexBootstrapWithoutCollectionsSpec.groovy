package io.micronaut.data.mongodb.index.validation.reactiveindexbootstrap

import io.micronaut.data.mongodb.index.validation.indexbootstrap.MongoIndexBootstrapWithoutCollectionsSpec

class MongoReactiveIndexBootstrapWithoutCollectionsSpec extends MongoIndexBootstrapWithoutCollectionsSpec {

    @Override
    Class<?> expectedCollectionsCreatorBeanType() {
        io.micronaut.data.mongodb.init.MongoReactiveCollectionsCreator
    }

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
