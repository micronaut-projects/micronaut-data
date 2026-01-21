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
package io.micronaut.data.r2dbc.postgres

import io.micronaut.context.ApplicationContext
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.tck.repositories.StreamingPersonReactorRepository
import io.micronaut.data.tck.tests.AbstractReactiveStreamingSpec
import reactor.core.publisher.Mono

import spock.lang.AutoCleanup
import spock.lang.Shared

class PostgresStreamingSpec extends AbstractReactiveStreamingSpec implements PostgresTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    R2dbcOperations r2dbcOperations = context.getBean(R2dbcOperations)

    @Override
    StreamingPersonReactorRepository getStreamingPersonReactorRepository() {
        return context.getBean(PostgresStreamingPersonRepository)
    }

    void seedPersons(long count) {
        Mono.from(r2dbcOperations.withConnection { c ->
            def sql = '''
                    INSERT INTO person(name, age, enabled)
                    SELECT 'Name ' || gs AS name,
                           (gs % 100) AS age,
                           TRUE
                    FROM generate_series(0, $1) AS gs
                '''.stripIndent()
            def stmt = c.createStatement(sql)
                    .bind(0, count - 1)
            return Mono.from(stmt.execute()).flatMap { r -> Mono.from(r.getRowsUpdated()) }
        }).block()
    }
}
