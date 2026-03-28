package io.micronaut.data.document.mongodb.reactive.geovalue.custom

import io.micronaut.data.document.mongodb.geovalue.custom.MongoCustomGeoPointValueIndexCreationSpec

class MongoReactiveCustomGeoPointValueIndexCreationSpec extends MongoCustomGeoPointValueIndexCreationSpec {

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
