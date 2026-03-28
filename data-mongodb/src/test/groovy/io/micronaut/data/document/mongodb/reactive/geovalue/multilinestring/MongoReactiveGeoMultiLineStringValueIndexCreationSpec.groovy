package io.micronaut.data.document.mongodb.reactive.geovalue.multilinestring

import io.micronaut.data.document.mongodb.geovalue.multilinestring.MongoGeoMultiLineStringValueIndexCreationSpec

class MongoReactiveGeoMultiLineStringValueIndexCreationSpec extends MongoGeoMultiLineStringValueIndexCreationSpec {

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
