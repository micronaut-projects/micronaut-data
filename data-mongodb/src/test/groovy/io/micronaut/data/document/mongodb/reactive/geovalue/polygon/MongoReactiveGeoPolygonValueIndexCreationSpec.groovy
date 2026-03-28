package io.micronaut.data.document.mongodb.reactive.geovalue.polygon

import io.micronaut.data.document.mongodb.geovalue.polygon.MongoGeoPolygonValueIndexCreationSpec

class MongoReactiveGeoPolygonValueIndexCreationSpec extends MongoGeoPolygonValueIndexCreationSpec {

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
