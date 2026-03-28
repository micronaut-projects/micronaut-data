package io.micronaut.data.document.mongodb.reactive.geovalue

import io.micronaut.data.document.mongodb.geovalue.MongoGeoPointValueIndexCreationSpec

class MongoReactiveGeoPointValueIndexCreationSpec extends MongoGeoPointValueIndexCreationSpec {

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
