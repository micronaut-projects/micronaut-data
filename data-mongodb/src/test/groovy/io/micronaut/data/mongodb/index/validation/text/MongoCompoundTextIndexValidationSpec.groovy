package io.micronaut.data.mongodb.index.validation.text

import io.micronaut.context.ApplicationContext
import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import spock.lang.Specification

class MongoCompoundTextIndexValidationSpec extends Specification implements MongoTestPropertyProvider {

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.mongodb.index.validation.text']
    }

    void 'fails fast when compound text fields are not adjacent'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true',
                'mongodb.package-names'                    : ['io.micronaut.data.mongodb.index.validation.text.adjacency']
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('must declare all text fields adjacently')
    }

    void 'fails fast when entity declares multiple text indexes'() {
        when:
        ApplicationContext.run(getProperties() + [
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.create-indexes'    : 'true',
                'mongodb.package-names'                    : ['io.micronaut.data.mongodb.index.validation.multipletext']
        ])

        then:
        def e = thrown(RuntimeException)
        e.message.contains('allows only one text index per collection')
    }
}
