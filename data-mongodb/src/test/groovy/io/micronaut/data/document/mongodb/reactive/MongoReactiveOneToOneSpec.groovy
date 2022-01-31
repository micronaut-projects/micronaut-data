package io.micronaut.data.document.mongodb.reactive

import io.micronaut.data.document.mongodb.MongoOneToOneSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest
class MongoReactiveOneToOneSpec extends MongoOneToOneSpec implements MongoSelectReactiveDriver {
}