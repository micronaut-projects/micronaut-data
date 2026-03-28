package io.micronaut.data.document.mongodb.reactive.geovalue.multipolygon

import io.micronaut.data.document.mongodb.geovalue.multipolygon.MongoGeoMultiPolygonValueIndexCreationSpec

class MongoReactiveGeoMultiPolygonValueIndexCreationSpec extends MongoGeoMultiPolygonValueIndexCreationSpec {

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
