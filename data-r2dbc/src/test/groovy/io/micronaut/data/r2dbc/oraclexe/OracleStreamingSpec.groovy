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
package io.micronaut.data.r2dbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.tck.repositories.StreamingPersonReactorRepository
import io.micronaut.data.tck.tests.AbstractReactiveStreamingSpec
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared

class OracleStreamingSpec extends AbstractReactiveStreamingSpec implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    R2dbcOperations r2dbcOperations = context.getBean(R2dbcOperations)

    @Override
    StreamingPersonReactorRepository getStreamingPersonReactorRepository() {
        return context.getBean(OracleStreamingPersonRepository)
    }

    @Override
    long getDefaultCount() {
        return 1_500_000L
    }

    @Override
    void seedPersons(long count) {
        Mono.from(r2dbcOperations.withTransaction { status ->
            r2dbcOperations.withConnection { c ->
                long step = 500_000L
                long full = count / step
                long remainder = count % step
                def sql = '''
                    INSERT INTO person(id, name, age, enabled)
                    SELECT (LEVEL - 1 + ?) AS id,
                           'Name ' || (LEVEL - 1 + ?) AS name,
                           MOD((LEVEL - 1 + ?), 100) AS age,
                           1
                    FROM DUAL
                    CONNECT BY LEVEL <= ?
                '''.stripIndent()
                def fullFlux = Flux.range(0, (int) full)
                        .concatMap { i ->
                            long offset = i * step
                            def stmt = c.createStatement(sql)
                                    .bind(0, offset)
                                    .bind(1, offset)
                                    .bind(2, offset)
                                    .bind(3, step)
                            Mono.from(stmt.execute())
                                    .flatMap { r -> Mono.from(r.getRowsUpdated()) }
                        }
                def tailMono = remainder > 0
                        ? Mono.from(
                                c.createStatement(sql)
                                        .bind(0, full * step)
                                        .bind(1, full * step)
                                        .bind(2, full * step)
                                        .bind(3, remainder)
                                        .execute()
                        ).flatMap { r -> Mono.from(r.getRowsUpdated()) }
                        : Mono.empty()
                return fullFlux.concatWith(tailMono as Publisher<? extends Long>).then(Mono.just(true))
            }
        }).block()
    }
}
