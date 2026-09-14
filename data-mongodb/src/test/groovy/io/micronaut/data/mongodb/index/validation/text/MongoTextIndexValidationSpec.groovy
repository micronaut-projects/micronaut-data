package io.micronaut.data.mongodb.index.validation.text

import io.micronaut.context.ApplicationContext
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import spock.lang.Specification

class MongoTextIndexValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.textweight']
    }

    void 'fails fast for invalid text weight'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true'
        ])

        then:
        def e = thrown(RuntimeException)
        e.cause != null
        e.cause.message.contains('Mongo text index weight must be greater than zero')
    }
}
