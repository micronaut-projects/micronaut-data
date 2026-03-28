package io.micronaut.data.document.mongodb.reactive.geocompound.options

import io.micronaut.data.document.mongodb.geocompound.options.MongoCompoundGeo2dOptionsIndexCreationSpec

class MongoReactiveCompoundGeo2dOptionsIndexCreationSpec extends MongoCompoundGeo2dOptionsIndexCreationSpec {

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
