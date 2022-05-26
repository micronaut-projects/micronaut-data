package io.micronaut.data.document.mongodb.reactive

import io.micronaut.data.document.mongodb.MongoMultiOneToManySpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest(transactional = false)
class MongoReactiveMultiOneToManySpec extends MongoMultiOneToManySpec implements MongoSelectReactiveDriver {
}