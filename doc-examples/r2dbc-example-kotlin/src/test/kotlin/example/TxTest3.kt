package example

import io.micronaut.data.annotation.IgnoreWhere
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import java.util.*
import javax.transaction.Transactional

@MicronautTest(transactional = false)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TxTest3 : AbstractTest(false) {
    @Inject
    lateinit var recordCoroutineRepository: RecordCoroutineRepository

    @Inject
    lateinit var recordTransactionalService: RecordTransactionalService

    @Inject
    lateinit var recordDeclarativeTransactionalService: RecordDeclarativeTransactionalService

    @Disabled // Pending feature: Flow doesn't propagate Reactor context
    @Test
    fun `coroutines returning flow inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordTransactionalService.saveAllUsingCoroutines(records).collect { }

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Disabled // Pending feature: Flow doesn't propagate Reactor context
    @Test
    fun `reactive streams returning flow inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordTransactionalService.saveAllUsingReactiveStreams(records).collect { }

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Test
    fun `coroutines suspending inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordTransactionalService.saveAllSuspendingUsingCoroutines(records)

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Disabled // Pending feature: Flow doesn't propagate Reactor context
    @Test
    fun `reactive streams suspending inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordTransactionalService.saveAllSuspendingUsingReactiveStreams(records)

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }


    @Disabled // Pending feature: Flow doesn't propagate Reactor context
    @Test
    fun `coroutines returning flow inside declarative transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordDeclarativeTransactionalService.saveAllUsingCoroutines(records).collect { }

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Test
    fun `reactive streams returning flow inside declarative transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordDeclarativeTransactionalService.saveAllUsingReactiveStreams(records).collect { }

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Test
    fun `coroutines suspending inside declarative transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordDeclarativeTransactionalService.saveAllSuspendingUsingCoroutines(records)

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Test
    fun `reactive streams suspending inside declarative transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordDeclarativeTransactionalService.saveAllSuspendingUsingReactiveStreams(records)

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

}


@Transactional(Transactional.TxType.MANDATORY)
@R2dbcRepository(dialect = Dialect.MYSQL)
interface RecordTransactionalCoroutineRepository : CoroutineCrudRepository<Record, UUID>

@Transactional(Transactional.TxType.MANDATORY)
@R2dbcRepository(dialect = Dialect.MYSQL)
interface RecordTransactionalReactiveStreamsRepository : ReactiveStreamsCrudRepository<Record, UUID>

@Singleton
@Transactional
open class RecordTransactionalService(
    private val coroutineRepository: RecordTransactionalCoroutineRepository,
    private val reactiveStreamsRepository: RecordTransactionalReactiveStreamsRepository,
) {
    open fun saveAllUsingCoroutines(records: Iterable<Record>): Flow<Record> = coroutineRepository.saveAll(records)

    open fun saveAllUsingReactiveStreams(records: Iterable<Record>): Flow<Record> =
        reactiveStreamsRepository.saveAll(records).asFlow()

    open suspend fun saveAllSuspendingUsingCoroutines(records: Iterable<Record>): List<Record> = records.map {
        coroutineRepository.save(it)
    }

    open suspend fun saveAllSuspendingUsingReactiveStreams(records: Iterable<Record>): List<Record> = records.map {
        reactiveStreamsRepository.save(it).awaitSingle()
    }
}

@Singleton
open class RecordDeclarativeTransactionalService(
    private val coroutineRepository: RecordTransactionalCoroutineRepository,
    private val reactiveStreamsRepository: RecordTransactionalReactiveStreamsRepository,
    private val r2dbcOperations: R2dbcOperations
) {
    open fun saveAllUsingCoroutines(records: Iterable<Record>): Flow<Record> = r2dbcOperations.withTransaction {
        coroutineRepository.saveAll(records).asFlux()
    }.asFlow()

    open fun saveAllUsingReactiveStreams(records: Iterable<Record>): Flow<Record> = r2dbcOperations.withTransaction {
        reactiveStreamsRepository.saveAll(records)
    }.asFlow()

    open suspend fun saveAllSuspendingUsingCoroutines(records: Iterable<Record>): List<Record> {
        return r2dbcOperations.withTransaction {
            mono {
                records.map {
                    coroutineRepository.save(it)
                }
            }
        }.awaitSingle()
    }

    open suspend fun saveAllSuspendingUsingReactiveStreams(records: Iterable<Record>): List<Record> {
        return r2dbcOperations.withTransaction {
            mono {
                records.map {
                    reactiveStreamsRepository.save(it).awaitSingle()
                }
            }
        }.awaitSingle()
    }
}
