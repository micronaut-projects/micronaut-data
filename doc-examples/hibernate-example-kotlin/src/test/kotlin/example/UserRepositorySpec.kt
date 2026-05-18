package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import org.hibernate.Session
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

@MicronautTest(transactional = false)
class UserRepositorySpec {

    @Inject
    private lateinit var userRepository: UserRepository

    @Inject
    private lateinit var transactionOperations: TransactionOperations<Session>

    @BeforeEach
    fun setup() {
        userRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        userRepository.deleteAll()
    }

    @Test
    fun testSoftDelete() {
        val joe = User(null, "Joe", true)
        val fred = User(null, "Fred", true)
        val bob = User(null, "Bob", true)
        userRepository.saveAll(
            listOf(
                fred,
                bob,
                joe
            )
        )

        userRepository.deleteById(joe.id!!)

        Assertions.assertEquals(2, userRepository.count())
        Assertions.assertTrue(userRepository.existsById(fred.id!!))
        Assertions.assertFalse(userRepository.existsById(joe.id!!))

        val disabled: MutableList<User> = userRepository.findDisabled()
        Assertions.assertEquals(1, disabled.size)
        Assertions.assertEquals("Joe", disabled.iterator().next().name)
    }

    @Test
    fun testStreaming() {
        val total = 500000L
        seedUsers(total)

        // tag::stream_and_count[]
        var entityCount: Long = transactionOperations.executeRead { status ->
            val session = status.connection
            var count = 0L
            userRepository.listAll().use { stream ->
                val n = AtomicLong()
                stream.forEach { p ->
                    session.detach(p)
                    if (n.incrementAndGet() % 50_000L == 0L) {
                        session.clear()
                    }
                    count++
                }
            }
            count
        }
        // end::stream_and_count[]
        Assertions.assertEquals(total, entityCount)

        entityCount = transactionOperations.executeRead { status ->
            val session = status.connection
            // Execute query with default fetch size
            var count = 0L
            userRepository.listAll().use { stream ->
                val n = AtomicLong()
                stream.forEach { p ->
                    session.detach(p)
                    if (n.incrementAndGet() % 50_000L == 0L) {
                        session.clear()
                    }
                    count++
                }
            }
            count
        }
        Assertions.assertEquals(total, entityCount)
    }

    fun seedUsers(count: Long) {
        val sql: String = """
            INSERT INTO user(id, name, enabled)
                   SELECT x, 'Name ' || x, true
                   FROM SYSTEM_RANGE(10, ?);

        """.trimIndent()
        transactionOperations.executeWrite { transaction ->
            val session = transaction.connection
            val query = session.createNativeMutationQuery(sql)
            query.setParameter(1, count + 10 - 1)
            query.executeUpdate()
            true
        }
    }
}
