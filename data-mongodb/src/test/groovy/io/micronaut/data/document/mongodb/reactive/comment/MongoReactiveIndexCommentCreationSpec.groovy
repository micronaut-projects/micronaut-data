package io.micronaut.data.document.mongodb.reactive.comment

import io.micronaut.data.document.mongodb.comment.MongoIndexCommentCreationSpec

class MongoReactiveIndexCommentCreationSpec extends MongoIndexCommentCreationSpec {

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
