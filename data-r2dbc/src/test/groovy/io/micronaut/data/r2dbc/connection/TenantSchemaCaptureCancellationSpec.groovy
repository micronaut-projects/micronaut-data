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
package io.micronaut.data.r2dbc.connection

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration
import io.micronaut.data.r2dbc.operations.R2dbcSchemaHandler
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.IsolationLevel
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class TenantSchemaCaptureCancellationSpec extends Specification {

    void "a tenant operation cancelled while the schema setting is read doesn't stop a later one of the scope from restoring it"() {
        given: "a connection whose first read of the schema setting never completes"
            def tenant = new AtomicReference<String>()
            def schema = new AtomicReference<String>('PUBLIC')
            def reads = new AtomicInteger()
            def firstReadStarted = Sinks.one()
            def connection = Stub(Connection) {
                isAutoCommit() >> true
                getTransactionIsolationLevel() >> IsolationLevel.READ_COMMITTED
                close() >> Mono.empty()
            }
            def factory = Stub(ConnectionFactory) {
                create() >> Mono.just(connection)
            }
            def schemaHandler = Stub(R2dbcSchemaHandler) {
                readSchemaSetting(_, _) >> { c, d ->
                    reads.incrementAndGet() == 1
                            ? Mono.never().doOnSubscribe { firstReadStarted.tryEmitValue(true) }
                            : Mono.just(schema.get())
                }
                useSchema(_, _, _) >> { c, d, String name -> Mono.fromRunnable { schema.set(name) } }
                restoreSchemaSetting(_, _, _) >> { c, d, String name -> Mono.fromRunnable { schema.set(name) } }
            }
            def operations = new DefaultR2dbcReactorConnectionOperations('default', factory,
                    new DataR2dbcConfiguration('default', factory, null), { tenant.get() } as SchemaTenantResolver, schemaHandler)

        when: "the first tenant operation is cancelled during the read, a second one switches the connection to the tenant"
            operations.withConnectionMono(ConnectionDefinition.DEFAULT, owner -> {
                tenant.set('foo')
                operations.withConnectionMono(ConnectionDefinition.DEFAULT, inner -> Mono.just(1))
                        .takeUntilOther(firstReadStarted.asMono())
                        .then(Mono.defer(() -> operations.withConnectionMono(ConnectionDefinition.DEFAULT, inner -> Mono.just(2))))
            }).block(Duration.ofSeconds(5))

        then: "the connection is switched back when the scope closes it"
            schema.get() == 'PUBLIC'
    }
}
