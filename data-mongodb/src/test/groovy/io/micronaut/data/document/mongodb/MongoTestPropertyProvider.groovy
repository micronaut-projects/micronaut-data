package io.micronaut.data.document.mongodb


import io.micronaut.test.support.TestPropertyProvider
import org.bson.UuidRepresentation

trait MongoTestPropertyProvider implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return [
                "micronaut.data.mongodb.driver-type": "sync",
                'micronaut.data.mongodb.create-collections': 'true',
                'mongodb.uuid-representation': UuidRepresentation.STANDARD.name(),
                'mongodb.package-names': getPackageNames()
        ]
    }

    List<String> getPackageNames() {
        ['io.micronaut.data']
    }

}

