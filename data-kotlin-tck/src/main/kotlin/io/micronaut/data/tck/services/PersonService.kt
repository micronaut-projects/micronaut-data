package io.micronaut.data.tck.services

import io.micronaut.context.ApplicationContext
import io.micronaut.data.tck.entities.Person
import io.micronaut.data.tck.repositories.PersonCoroutineRepository
import io.micronaut.data.tck.repositories.PersonCustomDbCoroutineRepository
import io.micronaut.data.tck.repositories.PersonCustomDbRepository
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.transaction.TransactionExecution
import io.micronaut.transaction.annotation.TransactionalAdvice
import io.micronaut.transaction.async.AsyncTransactionOperations
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.transaction.Transactional
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.lang.Thread.currentThread
import java.util.*

abstract class PersonService<T>(var beanContext: ApplicationContext) {

    @Inject
    private lateinit var beanContext2: ApplicationContext

    @Inject
    private lateinit var txManager: AsyncTransactionOperations<T>

    @Inject
    @Named("custom")
    private lateinit var txCustomManager: AsyncTransactionOperations<T>

    @Inject
    private lateinit var personSuspendRepository: PersonCoroutineRepository

    @Inject
    private lateinit var personSuspendRepositoryForCustomDb2: PersonCustomDbCoroutineRepository

    @Inject
    private lateinit var personRepository: PersonRepository

    @Inject
    private lateinit var personRepositoryForCustomDb: PersonCustomDbRepository

    open fun saveOne() {
        personRepository.save(Person("xyz"))
    }

    open fun saveOneForCustomDb() {
        personRepositoryForCustomDb.save(Person("xyz"))
    }

    open suspend fun saveOneSuspended() {
        personSuspendRepository.save(Person("xyz"))
    }

    open suspend fun saveOneSuspendedForCustomDb() {
        personSuspendRepositoryForCustomDb2.save(Person("xyz"))
    }

    @Transactional
    open suspend fun saveOneSuspended(p: Person) {
        personSuspendRepository.save(p)
    }

    @Transactional(Transactional.TxType.MANDATORY)
    open suspend fun saveOneMandatory(p: Person): TransactionExecution {
        val txStatus: TransactionExecution = getTxStatus()
        if (txStatus.isNewTransaction && txStatus.isCompleted) {
            throw IllegalStateException()
        }
        personSuspendRepository.save(p)
        return txStatus
    }

    @Transactional
    open suspend fun saveTwo(p1: Person, p2: Person) {
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
    open suspend fun saveForCustomDb(p: Person) {
        personSuspendRepositoryForCustomDb2.save(p)
    }

    @TransactionalAdvice("custom")
    open suspend fun deleteAllForCustomDb2(): TransactionExecution {
        val cu = beanContext.getBean(AsyncTransactionOperations::class.java, Qualifiers.byName("custom"))
        val txStatus: TransactionExecution = cu.findTransactionStatus().orElseThrow()
        if (txStatus.isCompleted || !txStatus.isNewTransaction) {
            throw RuntimeException()
        }
        personSuspendRepositoryForCustomDb2.deleteAll()
        return txStatus
    }

    @TransactionalAdvice("custom")
    open suspend fun saveForCustomDb2(p: Person): TransactionExecution {
        val cu = beanContext.getBean(AsyncTransactionOperations::class.java, Qualifiers.byName("custom"))
        val txStatus: TransactionExecution = cu.findTransactionStatus().orElseThrow()
        if (txStatus.isCompleted || !txStatus.isNewTransaction) {
            throw RuntimeException()
        }
        personSuspendRepositoryForCustomDb2.save(p)
        return txStatus
    }

    @Transactional
    open suspend fun customFind(id: Long): Optional<Person> {
        val threadName = currentThread().name
        delay(1000L)
        return withContext(IO) {
            if (threadName == currentThread().name) {
                throw IllegalStateException("Test requires a different thread!")
            }
            personSuspendRepository.queryById(id)
        }
    }

    @Transactional
    open fun txSave() {
        saveOne()
    }

    @Transactional
    open suspend fun txSaveAndFailSuspended() {
        saveOneSuspended()
        throw RuntimeException("myexception")
    }

    @Transactional
    open suspend fun txSaveNoSuspendedAndFailSuspended() {
        saveOne()
        throw RuntimeException("myexception")
    }

    @Transactional
    open suspend fun txSaveSuspended() {
        saveOneSuspended()
    }

    @Transactional
    open suspend fun txSaveAndCustomDbSave() {
        saveOneSuspended()
        saveOneSuspendedForCustomDb()
        throw RuntimeException("myexception")
    }

    @Transactional
    open suspend fun txSaveAndTxSaveCustom() {
        noTxSaveDefaultTxSaveCustom()
    }

    @TransactionalAdvice("custom") // Create a new method because @Transactional is not repeatable
    open suspend fun noTxSaveDefaultTxSaveCustom() {
        saveOneSuspended()
        saveOneSuspendedForCustomDb()
        throw RuntimeException("myexception")
    }

    open fun count(): Long {
        val count = personRepository.count()
        LoggerFactory.getLogger(this::class.java).info("Stored $count records")
        return count
    }

    open fun countForCustomDb(): Long {
        val count = personRepositoryForCustomDb.count()
        LoggerFactory.getLogger(this::class.java).info("Stored $count records")
        return count
    }

    suspend fun suspendCount(): Long {
        val count = personSuspendRepository.count()
        LoggerFactory.getLogger(this::class.java).info("Stored $count records")
        return count
    }

    suspend fun suspendCountForCustomDb(): Long {
        val count = personSuspendRepositoryForCustomDb2.count()
        LoggerFactory.getLogger(this::class.java).info("Stored custom $count records")
        return count
    }

    private fun getTxStatus() =
        txManager.findTransactionStatus().orElse(null)

    private fun getCustomTxStatus() =
        txCustomManager.findTransactionStatus().orElse(null)

}
