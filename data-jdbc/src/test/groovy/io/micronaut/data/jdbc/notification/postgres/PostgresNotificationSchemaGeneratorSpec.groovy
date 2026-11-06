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
package io.micronaut.data.jdbc.notification.postgres

import spock.lang.Specification

class PostgresNotificationSchemaGeneratorSpec extends Specification {

    void "qualifies the bundled #name function in the target schema"() {
        given:
        def sql = getClass().getResource('/io/micronaut/data/jdbc/notification/postgres/sql/' + resource).getText('UTF-8')

        when:
        def qualified = PostgresNotificationSchemaGenerator.qualifyFunction(sql, name, '"Sales"."' + name + '"')

        then:
        qualified.contains('CREATE OR REPLACE FUNCTION "Sales"."' + name + '"()')
        !qualified.contains('CREATE OR REPLACE FUNCTION ' + name + '()')

        where:
        name                                 | resource
        'micronaut_data_notify_change'        | 'create-notification-function.sql'
        'micronaut_data_notify_invalidation'  | 'create-invalidation-function.sql'
    }

    void "rejects a resource whose function declaration no longer matches: #declaration"() {
        when:
        PostgresNotificationSchemaGenerator.qualifyFunction(declaration, 'micronaut_data_notify_change',
            '"Sales"."micronaut_data_notify_change"')

        then:
        def failure = thrown(IllegalStateException)
        failure.message.contains('missing declaration [CREATE OR REPLACE FUNCTION micronaut_data_notify_change()]')

        where:
        declaration << [
            'create or replace function micronaut_data_notify_change()',
            'CREATE OR REPLACE FUNCTION micronaut_data_notify_change\n()',
            'CREATE OR REPLACE FUNCTION  micronaut_data_notify_change()',
            'CREATE OR REPLACE FUNCTION another_function()'
        ]
    }
}
