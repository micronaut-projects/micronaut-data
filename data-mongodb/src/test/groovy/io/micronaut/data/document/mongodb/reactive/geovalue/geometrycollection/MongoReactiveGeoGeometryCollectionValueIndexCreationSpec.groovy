package io.micronaut.data.document.mongodb.reactive.geovalue.geometrycollection

import io.micronaut.data.document.mongodb.geovalue.geometrycollection.MongoGeoGeometryCollectionValueIndexCreationSpec

class MongoReactiveGeoGeometryCollectionValueIndexCreationSpec extends MongoGeoGeometryCollectionValueIndexCreationSpec {

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
