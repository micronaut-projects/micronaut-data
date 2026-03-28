package io.micronaut.data.document.mongodb.reactive.geo

import io.micronaut.data.document.mongodb.geo.MongoGeoIndexCreationSpec

class MongoReactiveGeoIndexCreationSpec extends MongoGeoIndexCreationSpec {

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
