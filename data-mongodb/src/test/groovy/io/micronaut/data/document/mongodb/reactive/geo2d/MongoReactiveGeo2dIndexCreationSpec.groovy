package io.micronaut.data.document.mongodb.reactive.geo2d

import io.micronaut.data.document.mongodb.geo2d.MongoGeo2dIndexCreationSpec

class MongoReactiveGeo2dIndexCreationSpec extends MongoGeo2dIndexCreationSpec {

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
