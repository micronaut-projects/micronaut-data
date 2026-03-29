package io.micronaut.data.document.mongodb.validation.reactiveexistingindexname

import io.micronaut.data.document.mongodb.validation.existingindexname.MongoExistingIndexNameReconciliationSpec

class MongoReactiveExistingIndexNameReconciliationSpec extends MongoExistingIndexNameReconciliationSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
