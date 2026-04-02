package io.micronaut.data.mongodb.index.validation.textversionconflict

class MongoReactiveTextIndexVersionConflictSpec extends MongoTextIndexVersionConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
