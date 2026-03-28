package io.micronaut.data.document.mongodb.reactive.geovalue.implicit

import io.micronaut.data.document.mongodb.geovalue.implicit.MongoImplicitCustomGeoPointValueIndexCreationSpec

class MongoReactiveImplicitCustomGeoPointValueIndexCreationSpec extends MongoImplicitCustomGeoPointValueIndexCreationSpec {

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
