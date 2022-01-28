package io.micronaut.data.document.mongodb.reactive


import io.micronaut.data.document.mongodb.MongoManyToManySpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest
class MongoReactiveManyToManySpec extends MongoManyToManySpec implements MongoSelectReactiveDriver {
}