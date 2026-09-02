package io.micronaut.data.mongodb.index.validation.existingindexadvancedconflict

class MongoReactiveExistingIndexAdvancedConflictSpec extends MongoExistingIndexAdvancedConflictSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
