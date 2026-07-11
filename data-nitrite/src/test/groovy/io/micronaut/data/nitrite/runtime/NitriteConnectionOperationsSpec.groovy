package io.micronaut.data.nitrite.runtime

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.data.nitrite.transaction.NitriteConnectionOperations
import io.micronaut.data.nitrite.transaction.NitriteTransactionContext
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder
import io.micronaut.data.nitrite.transaction.DefaultNitriteTransactionOperations
import io.micronaut.data.nitrite.transaction.NitriteTransactionOperations
import io.micronaut.transaction.exceptions.NoTransactionException
import io.micronaut.transaction.impl.DefaultTransactionStatus
import org.dizitart.no2.Nitrite
import org.dizitart.no2.transaction.Session
import org.dizitart.no2.transaction.Transaction
import spock.lang.Specification

class NitriteConnectionOperationsSpec extends Specification {

    void "test NitriteConnectionOperations and NitriteTransactionOperations"() {
        given:
        Nitrite database = Nitrite.builder().openOrCreate()
        NitriteConnectionOperations connectionOperations = new NitriteConnectionOperations(database)
        NitriteTransactionHolder holder = new NitriteTransactionHolder()
        NitriteTransactionOperations transactionManager = new DefaultNitriteTransactionOperations(connectionOperations, connectionOperations, holder)
        
        when:
        ConnectionDefinition definition = ConnectionDefinition.DEFAULT
        Session session = connectionOperations.openConnection(definition)
        
        then:
        session != null

        when:
        io.micronaut.data.connection.support.DefaultConnectionStatus<Session> status = new io.micronaut.data.connection.support.DefaultConnectionStatus<>(
            session, definition, true, connectionOperations
        )
        connectionOperations.setupConnection(status)
        
        then:
        noExceptionThrown()
        
        when:
        connectionOperations.closeConnection(status)
        
        then:
        noExceptionThrown()

        when:
        transactionManager.getConnection()

        then:
        thrown(NoTransactionException)

        when:
        Session activeSession = database.createSession()
        DefaultTransactionStatus<Session> txStatus = io.micronaut.transaction.impl.DefaultTransactionStatus.newTx(
            new io.micronaut.data.connection.support.DefaultConnectionStatus<>(activeSession, definition, true, connectionOperations),
            io.micronaut.transaction.TransactionDefinition.DEFAULT,
            transactionManager
        )
        transactionManager.doBegin(txStatus)

        then:
        txStatus.getTransaction() != null
        holder.get() != null
        
        cleanup:
        activeSession?.close()
        database?.close()
    }
}
