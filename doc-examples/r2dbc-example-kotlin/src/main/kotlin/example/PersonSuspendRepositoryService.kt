package example

import io.micronaut.transaction.TransactionExecution
import io.micronaut.transaction.annotation.TransactionalAdvice
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import io.micronaut.transaction.support.TransactionSynchronizationManager
import io.r2dbc.spi.Connection
import jakarta.inject.Named
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import reactor.util.context.ContextView
import java.lang.Thread.currentThread
import java.util.*
import javax.transaction.Transactional

@Singleton
open class PersonSuspendRepositoryService(
        private val txManager: ReactorReactiveTransactionOperations<Connection>,
        @Named("custom") private val txCustomManager: ReactorReactiveTransactionOperations<Connection>,
        private val parentSuspendRepository: ParentSuspendRepository,
        private val parentSuspendRepositoryForCustomDb: ParentSuspendRepositoryForCustomDb,
        private val parentRepository: ParentRepository,
        private val parentRepositoryForCustomDb: ParentRepositoryForCustomDb) {

    open fun saveOne() {
        parentRepository.save(Parent("xyz", Collections.emptyList()))
    }

    open fun saveOneForCustomDb() {
        parentRepositoryForCustomDb.save(Parent("xyz", Collections.emptyList()))
    }

    open suspend fun saveOneSuspended() {
        parentSuspendRepository.save(Parent("xyz", Collections.emptyList()))
    }

    open suspend fun saveOneSuspendedForCustomDb() {
        parentSuspendRepositoryForCustomDb.save(Parent("xyz", Collections.emptyList()))
    }

    @Transactional
    open suspend fun saveOneSuspended(p: Parent) {
        parentSuspendRepository.save(p)
    }

    @Transactional(Transactional.TxType.MANDATORY)
    open suspend fun saveOneMandatory(p: Parent): TransactionExecution {
        val txStatus: TransactionExecution = getTxStatus()
        if (txStatus.isNewTransaction && txStatus.isCompleted) {
            throw IllegalStateException()
        }
        parentSuspendRepository.save(p)
        return txStatus
    }

    @Transactional
    open suspend fun saveTwo(p1: Parent, p2: Parent) {
        val current: TransactionExecution = getTxStatus()
        if (!current.isNewTransaction && current.isCompleted) {
            throw IllegalStateException()
        }
        val txStatus1 = saveOneMandatory(p1)
        val txStatus2 = saveOneMandatory(p2)
        if (current == txStatus1 || current == txStatus2 || txStatus1 == txStatus2) {
            throw IllegalStateException()
        }
        if (txStatus1.isNewTransaction || txStatus1.isCompleted || txStatus2.isNewTransaction || txStatus1.isCompleted) {
            throw IllegalStateException()
        }
    }

    @Transactional
    open suspend fun saveForCustomDb(p: Parent) {
        parentSuspendRepositoryForCustomDb.save(p)
    }

    @TransactionalAdvice("custom")
    open suspend fun deleteAllForCustomDb2(): TransactionExecution {
        val txStatus: TransactionExecution = getCustomTxStatus()
        if (txStatus.isCompleted || !txStatus.isNewTransaction) {
            throw RuntimeException()
        }
        parentSuspendRepositoryForCustomDb.deleteAll()
        return txStatus
    }

    @TransactionalAdvice("custom")
    open suspend fun saveForCustomDb2(p: Parent): TransactionExecution {
        val txStatus: TransactionExecution = getCustomTxStatus()
        if (txStatus.isCompleted || !txStatus.isNewTransaction) {
            throw RuntimeException()
        }
        parentSuspendRepositoryForCustomDb.save(p)
        return txStatus
    }

    @Transactional
    open suspend fun customFind(id: Int): Optional<Parent> {
        val threadName = currentThread().name
        delay(1000L)
        return withContext(IO) {
            if (threadName == currentThread().name) {
                throw IllegalStateException("Test requires a different thread!")
            }
            parentSuspendRepository.queryById(id)
        }
    }

    @Transactional
    open fun normalStore() {
        saveOne()
    }

    @Transactional
    open suspend fun coroutinesStore() {
        saveOneSuspended()
        throw RuntimeException("myexception")
    }

    @Transactional
    open suspend fun coroutinesGenericStore() {
        saveOne()
        throw RuntimeException("myexception")
    }

    @Transactional
    open suspend fun storeCoroutines() {
        saveOneSuspended()
    }

    @Transactional
    open suspend fun coroutinesStoreWithCustomDBNotTransactional() {
        saveOneSuspended()
        saveOneSuspendedForCustomDb()
        throw RuntimeException("myexception")
    }

    @Transactional
    open suspend fun coroutinesStoreWithCustomDBTransactional() {
        coroutinesStoreWithCustomDBTransactional2()
    }

    @TransactionalAdvice("custom") // Create a new method because @Transactional is not repeatable
    open suspend fun coroutinesStoreWithCustomDBTransactional2() {
        saveOneSuspended()
        saveOneSuspendedForCustomDb()
        throw RuntimeException("myexception")
    }

    open fun count(): Long {
        val count = parentRepository.count()
        LoggerFactory.getLogger(this::class.java).info("Stored $count records")
        return count
    }

    open fun countForCustomDb(): Long {
        val count = parentRepositoryForCustomDb.count()
        LoggerFactory.getLogger(this::class.java).info("Stored $count records")
        return count
    }

    suspend fun suspendCount(): Long {
        val count = parentSuspendRepository.count()
        LoggerFactory.getLogger(this::class.java).info("Stored $count records")
        return count
    }

    suspend fun suspendCountForCustomDb(): Long {
        val count = parentSuspendRepositoryForCustomDb.count()
        LoggerFactory.getLogger(this::class.java).info("Stored $count records")
        return count
    }

    private fun getTxStatus() =
            txManager.getTransactionStatus(TransactionSynchronizationManager.getResource(ContextView::class.java) as ContextView)

    private fun getCustomTxStatus() =
            txCustomManager.getTransactionStatus(TransactionSynchronizationManager.getResource(ContextView::class.java) as ContextView)

}
