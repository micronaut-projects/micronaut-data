package io.micronaut.data.document.mongodb.reactive

import io.micronaut.data.document.mongodb.MongoProjectionSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest

@MicronautTest(transactional = false)
class MongoReactiveProjectionSpec extends MongoProjectionSpec implements MongoSelectReactiveDriver {
}
