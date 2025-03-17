package example

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.annotation.Connectable
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.operations.R2dbcOperations
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import io.micronaut.data.repository.reactive.ReactiveStreamsCrudRepository
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.data.connection.kotlin.CoroutineConnectionOperations
import io.micronaut.transaction.kotlin.CoroutineTransactionOperations
import io.r2dbc.spi.Connection
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.asFlux
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import java.util.*
import jakarta.transaction.Transactional
import kotlinx.coroutines.flow.toList

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

    @Inject
    lateinit var recordDeclarativeCoroutineTransactionalService: RecordDeclarativeCoroutineTransactionalService

    @Inject
    lateinit var recordCoroutineTransactionalService: RecordCoroutineTransactionalService

    @Inject
    lateinit var recordCoroutineConnectionService: RecordDeclarativeCoroutineConnectionService

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

    @Test
    fun `reactive streams suspending inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordTransactionalService.saveAllSuspendingUsingReactiveStreams(records)

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }


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

    @Disabled // Not supported
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

    @Disabled // Pending feature: Flow doesn't propagate Reactor context
    @Test
    fun `coroutines TX operations - coroutines returning flow inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordCoroutineTransactionalService.saveAllAsFlow(records).collect { }

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Test
    fun `coroutines TX operations - coroutines suspending inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordCoroutineTransactionalService.saveAllAsList(records)

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Disabled // Pending feature: Flow doesn't propagate Reactor context
    @Test
    fun `coroutines TX operations - coroutines returning flow inside declarative transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordDeclarativeCoroutineTransactionalService.saveAllAsFlow(records).collect { }

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Test
    fun `coroutines TX operations - coroutines suspending inside declarative transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordDeclarativeCoroutineTransactionalService.saveAllAsList(records)

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Disabled // Pending feature: Flow doesn't propagate Reactor context
    @Test
    fun `coroutines connection operations - coroutines returning flow inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordCoroutineConnectionService.saveAllAsFlow(records).collect { }

        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[0].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[0].bar))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByFoo(records[1].foo))
        Assertions.assertEquals(true, recordCoroutineRepository.existsByBar(records[1].bar))
    }

    @Test
    fun `coroutines connection operations - coroutines suspending inside transaction`(): Unit = runBlocking {
        val records = (1..2).map { Record(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()) }

        recordCoroutineConnectionService.saveAllAsList(records)

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

@Transactional
@Singleton
open class RecordCoroutineTransactionalService(
    private val coroutineRepository: RecordTransactionalCoroutineRepository,
) {
    open fun saveAllAsFlow(records: Iterable<Record>): Flow<Record> {
        return coroutineRepository.saveAll(records)
    }

    open suspend fun saveAllAsList(records: Iterable<Record>): List<Record> {
        return coroutineRepository.saveAll(records).toList()
    }
}

@Singleton
open class RecordDeclarativeCoroutineTransactionalService(
    private val coroutineRepository: RecordTransactionalCoroutineRepository,
    private val coroutineTransactionalService: CoroutineTransactionOperations<Connection>
) {
    open suspend fun saveAllAsFlow(records: Iterable<Record>): Flow<Record> = coroutineTransactionalService.execute {
        coroutineRepository.saveAll(records)
    }

    open suspend fun saveAllAsList(records: Iterable<Record>): List<Record> = coroutineTransactionalService.execute {
        coroutineRepository.saveAll(records).toList()
    }
}

@Singleton
open class RecordDeclarativeCoroutineConnectionService(
    private val coroutineRepository: RecordTransactionalCoroutineRepository,
    private val coroutineConnectionService: CoroutineConnectionOperations<Connection>
) {
    open suspend fun saveAllAsFlow(records: Iterable<Record>): Flow<Record> = coroutineConnectionService.execute {
        saveAllAsFlow0(records)
    }

    open suspend fun saveAllAsList(records: Iterable<Record>): List<Record> = coroutineConnectionService.execute {
        saveAllAsList0(records)
    }

    @Connectable(propagation = ConnectionDefinition.Propagation.MANDATORY)
    open suspend fun saveAllAsFlow0(records: Iterable<Record>) = saveAllAsFlow1(records)

    @Transactional
    open suspend fun saveAllAsFlow1(records: Iterable<Record>) = coroutineRepository.saveAll(records)

    @Connectable(propagation = ConnectionDefinition.Propagation.MANDATORY)
    open suspend fun saveAllAsList0(records: Iterable<Record>) = saveAllAsList1(records)

    @Transactional
    open suspend fun saveAllAsList1(records: Iterable<Record>) = coroutineRepository.saveAll(records).toList()
}
