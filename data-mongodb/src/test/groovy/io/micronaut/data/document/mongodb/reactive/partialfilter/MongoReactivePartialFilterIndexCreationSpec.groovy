package io.micronaut.data.document.mongodb.reactive.partialfilter

import io.micronaut.data.document.mongodb.partialfilter.MongoPartialFilterIndexCreationSpec

class MongoReactivePartialFilterIndexCreationSpec extends MongoPartialFilterIndexCreationSpec {

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
