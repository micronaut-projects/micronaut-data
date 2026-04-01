package io.micronaut.data.mongodb.index.validation.reactiveexistingindexname

import io.micronaut.data.mongodb.index.validation.existingindexname.MongoExistingIndexNameReconciliationSpec

class MongoReactiveExistingIndexNameReconciliationSpec extends MongoExistingIndexNameReconciliationSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
