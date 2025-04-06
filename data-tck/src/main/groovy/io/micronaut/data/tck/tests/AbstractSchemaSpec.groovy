package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractSchemaSpec extends Specification {

    abstract Map<String, String> getProperties()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ["datasources.default.packages": "io.micronaut.data.tck.entities.schema"])

    ApplicationContext getApplicationContext() {
        return context
    }

    /**
     * Validates created schema using entities from given package.
     */
    void 'validate schema'() {
        when:
        def schemaValidateProperties = properties + ["datasources.default.schema-generate": "validate",
                                                     "datasources.default.packages": "io.micronaut.data.tck.entities.schema"]
        ApplicationContext.run(schemaValidateProperties)
        then:
        noExceptionThrown()
    }
}
