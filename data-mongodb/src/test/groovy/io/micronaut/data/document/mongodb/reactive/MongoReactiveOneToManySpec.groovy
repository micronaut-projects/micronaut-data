package io.micronaut.data.document.mongodb.reactive

import io.micronaut.data.document.mongodb.MongoOneToManySpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest(transactional = false)
class MongoReactiveOneToManySpec extends MongoOneToManySpec implements MongoSelectReactiveDriver {
}