package io.micronaut.data.document.mongodb


import io.micronaut.test.support.TestPropertyProvider
import org.bson.UuidRepresentation

trait MongoTestPropertyProvider implements TestPropertyProvider {

    int sharedSpecsCount() {
        return 1
    }

    def cleanupSpec() {
        TestContainerHolder.cleanup(sharedSpecsCount())
    }

    @Override
    Map<String, String> getProperties() {
        def mongo = TestContainerHolder.getContainerOrCreate()
        mongo.start()
        return [
                'micronaut.data.mongo.create-collections': 'true',
                'mongodb.uri': mongo.replicaSetUrl,
                'mongodb.uuid-representation': UuidRepresentation.STANDARD.name()
        ]
    }


}

