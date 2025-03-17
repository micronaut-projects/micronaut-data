package example


import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.*


@JdbcRepository(dialect = Dialect.H2)
abstract class TestRepository: CoroutineCrudRepository<TestEntity, UUID> {
    abstract suspend fun findByScheduledTimeBeforeAndAttemptsLessThanEquals(scheduledTime: Instant, attempts: Int): Flow<TestEntity>

    /**
     * Showcase that making this return a List instead of Flow works... idk the implications of this. Does it internally
     * call Flow.toList()?
     */
    abstract suspend fun findByScheduledTimeBefore(scheduledTime: Instant): List<TestEntity>
}
