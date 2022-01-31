package io.micronaut.data.document.mongodb.reactive


import io.micronaut.data.document.mongodb.MongoManyToOneSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest
class MongoReactiveManyToOneSpec extends MongoManyToOneSpec implements MongoSelectReactiveDriver {
}