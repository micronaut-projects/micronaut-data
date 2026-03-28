package io.micronaut.data.document.mongodb.reactive.geovalue.linestring

import io.micronaut.data.document.mongodb.geovalue.linestring.MongoGeoLineStringValueIndexCreationSpec

class MongoReactiveGeoLineStringValueIndexCreationSpec extends MongoGeoLineStringValueIndexCreationSpec {

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
