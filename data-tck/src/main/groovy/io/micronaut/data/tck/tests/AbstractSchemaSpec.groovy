/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
