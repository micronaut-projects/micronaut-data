package io.micronaut.data.document.mongodb.reactive


import io.micronaut.data.document.mongodb.MongoManyToOneSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest(transactional = false)
class MongoReactiveManyToOneSpec extends MongoManyToOneSpec implements MongoSelectReactiveDriver {
}