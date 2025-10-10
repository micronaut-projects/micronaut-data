package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

abstract class AbstractSchemaSpec extends Specification {

    abstract Map<String, String> getProperties()

    /**
     * Validates created schema using entities from given package.
     */
    void 'validate schema'() {
        given:
        def props = properties
        props["datasources.default.packages"] = "io.micronaut.data.tck.entities.schema"
        def initialContext = ApplicationContext.run(props)
        when:
        def schemaValidateProperties = props
        schemaValidateProperties["datasources.default.schema-generate"] =  "validate"
        def validationContext = ApplicationContext.run(schemaValidateProperties)
        then:
        noExceptionThrown()
        cleanup:
        if (initialContext) {
            initialContext.close()
        }
        if (validationContext) {
            validationContext.close()
        }
    }
}
