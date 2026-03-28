package io.micronaut.data.document.mongodb.reactive.text

import io.micronaut.data.document.mongodb.text.MongoAggregatedTextIndexCreationSpec

class MongoReactiveAggregatedTextIndexCreationSpec extends MongoAggregatedTextIndexCreationSpec {

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
