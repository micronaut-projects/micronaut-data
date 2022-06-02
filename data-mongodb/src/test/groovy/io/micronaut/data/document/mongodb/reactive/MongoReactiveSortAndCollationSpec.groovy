package io.micronaut.data.document.mongodb.reactive

import io.micronaut.data.document.mongodb.MongoSortAndCollationSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest(transactional = false)
class MongoReactiveSortAndCollationSpec extends MongoSortAndCollationSpec implements MongoSelectReactiveDriver {
}
