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
open class PersonSuspendRepositoryService(@Named("custom") private val txManager: ReactorReactiveTransactionOperations<Connection>,
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

    @Transactional
    open suspend fun saveForCustomDb(p: Parent) {
        parentSuspendRepositoryForCustomDb.save(p)
    }

    @TransactionalAdvice("custom")
    open suspend fun deleteAllForCustomDb2() : TransactionExecution {
        val txStatus: TransactionExecution = txManager.getTransactionStatus(TransactionSynchronizationManager.getResource(ContextView::class.java) as ContextView)
        if (txStatus.isCompleted || !txStatus.isNewTransaction) {
            throw RuntimeException()
        }
        parentSuspendRepositoryForCustomDb.deleteAll()
        return txStatus
    }

    @TransactionalAdvice("custom")
    open suspend fun saveForCustomDb2(p: Parent) : TransactionExecution  {
        val txStatus: TransactionExecution = txManager.getTransactionStatus(TransactionSynchronizationManager.getResource(ContextView::class.java) as ContextView)
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
        throw RuntimeException("exception")
    }

    @Transactional
    open suspend fun coroutinesStore() {
        saveOneSuspended()
        throw RuntimeException("exception")
    }

    @Transactional
    open suspend fun coroutinesGenericStore() {
        saveOne()
        throw RuntimeException("exception")
    }

    @Transactional
    open suspend fun storeCoroutines() {
        saveOneSuspended()
    }

    @Transactional
    open fun normalWithCustomDSNotTransactional() {
        saveOne()
        saveOneForCustomDb()
        throw RuntimeException("exception")
    }

    @Transactional
    open fun normalWithCustomDSTransactional() {
        normalWithCustomDSTransactional2()
    }

    @TransactionalAdvice("custom") // Create a new method because @Transactional is not repeatable
    open fun normalWithCustomDSTransactional2() {
        saveOne()
        saveOneForCustomDb()
        throw RuntimeException("exception")
    }

    @Transactional
    open suspend fun coroutinesStoreWithCustomDBNotTransactional() {
        saveOneSuspended()
        saveOneSuspendedForCustomDb()
        throw RuntimeException("exception")
    }

    @Transactional
    open suspend fun coroutinesStoreWithCustomDBTransactional() {
        coroutinesStoreWithCustomDBTransactional2()
    }

    @TransactionalAdvice("custom") // Create a new method because @Transactional is not repeatable
    open suspend fun coroutinesStoreWithCustomDBTransactional2() {
        saveOneSuspended()
        saveOneSuspendedForCustomDb()
        throw RuntimeException("exception")
    }

    @Transactional
    open suspend fun coroutinesGenericStoreWithCustomDb() {
        coroutinesGenericStoreWithCustomDb2()
    }

    @TransactionalAdvice("custom") // Create a new method because @Transactional is not repeatable
    open fun coroutinesGenericStoreWithCustomDb2() {
        saveOne()
        saveOneForCustomDb()
        throw RuntimeException("exception")
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

    open fun justError() {
        throw RuntimeException("exception")
    }

}
