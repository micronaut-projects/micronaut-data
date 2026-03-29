package io.micronaut.data.document.mongodb.reactive.query

import io.micronaut.data.document.mongodb.query.MongoCriteriaQueryOperatorsExecutionSpec

class MongoReactiveCriteriaQueryOperatorsExecutionSpec extends MongoCriteriaQueryOperatorsExecutionSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }
}
