package io.micronaut.data.document.mongodb.reactive

import io.micronaut.data.document.mongodb.MongoTestPropertyProvider

trait MongoSelectReactiveDriver implements MongoTestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return super.getProperties() + [
                "micronaut.data.mongodb.driver-type": "reactive"
        ]
    }

}
