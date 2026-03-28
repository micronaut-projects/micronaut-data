package io.micronaut.data.document.mongodb.reactive.geocompound

import io.micronaut.data.document.mongodb.geocompound.MongoCompoundGeoIndexCreationSpec

class MongoReactiveCompoundGeoIndexCreationSpec extends MongoCompoundGeoIndexCreationSpec {

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
