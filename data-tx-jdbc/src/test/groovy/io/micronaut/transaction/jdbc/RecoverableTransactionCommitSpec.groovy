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
package io.micronaut.transaction.jdbc

import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.data.connection.DefaultConnectionDefinition
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.transaction.TransactionStatus
import io.micronaut.transaction.impl.DefaultTransactionStatus
import io.micronaut.transaction.recovery.CommitOutcome
import io.micronaut.transaction.recovery.CommitOutcomeResolver
import io.micronaut.transaction.recovery.RecoverableTransactionContext
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection

class RecoverableTransactionCommitSpec extends Specification {

    def "captures the recovery token immediately before JDBC commit"() {
        given:
        def events = []
        def connection = Mock(Connection)
        def connectionStatus = new DefaultConnectionStatus<>(connection, new DefaultConnectionDefinition('test'), true, null)
        def transactionManager = new DataSourceTransactionManager(
            Mock(DataSource),
            Mock(io.micronaut.data.connection.ConnectionOperations),
            Mock(io.micronaut.data.connection.SynchronousConnectionManager)
        )
        def transactionStatus = DefaultTransactionStatus.newTx(connectionStatus, io.micronaut.transaction.TransactionDefinition.DEFAULT, transactionManager)
        def recoveryContext = new RecoverableTransactionContext()
        recoveryContext.configure(new CommitOutcomeResolver() {
            @Override
            Object captureLtxid(TransactionStatus<?> status) {
                assert status.is(transactionStatus)
                events << 'capture'
                'ltxid'
            }

            @Override
            CommitOutcome resolve(Object token) {
                throw new UnsupportedOperationException('Outcome resolution is not part of commit')
            }
        })

        when:
        PropagatedContext.getOrEmpty().plus(recoveryContext).propagate {
            transactionManager.doCommit(transactionStatus)
        }

        then:
        1 * connection.commit() >> { events << 'commit' }
        recoveryContext.token == 'ltxid'
        events == ['capture', 'commit']
    }
}
