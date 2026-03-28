package io.micronaut.data.document.mongodb.reactive.geo2d.options

import io.micronaut.data.document.mongodb.geo2d.options.MongoGeo2dOptionsIndexCreationSpec

class MongoReactiveGeo2dOptionsIndexCreationSpec extends MongoGeo2dOptionsIndexCreationSpec {

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
