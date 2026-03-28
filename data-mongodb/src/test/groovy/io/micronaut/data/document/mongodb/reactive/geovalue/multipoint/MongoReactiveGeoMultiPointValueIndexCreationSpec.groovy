package io.micronaut.data.document.mongodb.reactive.geovalue.multipoint

import io.micronaut.data.document.mongodb.geovalue.multipoint.MongoGeoMultiPointValueIndexCreationSpec

class MongoReactiveGeoMultiPointValueIndexCreationSpec extends MongoGeoMultiPointValueIndexCreationSpec {

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
