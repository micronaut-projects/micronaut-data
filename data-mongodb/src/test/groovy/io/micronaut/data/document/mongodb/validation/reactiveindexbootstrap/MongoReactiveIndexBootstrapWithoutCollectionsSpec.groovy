package io.micronaut.data.document.mongodb.validation.reactiveindexbootstrap

import io.micronaut.data.document.mongodb.validation.indexbootstrap.MongoIndexBootstrapWithoutCollectionsSpec

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
