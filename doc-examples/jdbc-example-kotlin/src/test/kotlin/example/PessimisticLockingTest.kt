package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.util.concurrent.Executors

@MicronautTest(transactional = false)
class PessimisticLockingTest {

    @Inject
    lateinit var accountBalanceRepository: AccountBalanceRepository

    @Inject
    lateinit var productLockingRepository: ProductLockingRepository

    @Inject
    lateinit var manufacturerRepository: ManufacturerRepository

    @AfterEach
    fun cleanup() {
        accountBalanceRepository.deleteAll()
        productLockingRepository.deleteAll()
        manufacturerRepository.deleteAll()
    }

    @Test
    fun `concurrent updates do not lose writes`() {
        val id = accountBalanceRepository.save(AccountBalance(null, BigInteger.valueOf(100))).id!!

        val executor = Executors.newFixedThreadPool(4)
        try {
            (1..20).map {
                executor.submit { accountBalanceRepository.addToBalance(id, BigInteger.TEN) }
            }.forEach { it.get() }
        } finally {
            executor.shutdown()
        }

        assertEquals(BigInteger.valueOf(300), accountBalanceRepository.findById(id)!!.balance)
    }

    @Test
    fun `find methods for update`() {
        val apple = manufacturerRepository.save("Apple")
        val macBook = productLockingRepository.save(Product(null, "MacBook", apple))
        productLockingRepository.save(Product(null, "iPhone", apple))

        val found = productLockingRepository.findByIdForUpdate(macBook.id!!)!!
        assertEquals("Apple", found.manufacturer?.name)

        assertEquals(listOf("MacBook", "iPhone"), productLockingRepository.findAllOrderByNameForUpdate().map { it.name })

        assertEquals(listOf("iPhone"), productLockingRepository.findByNameForUpdate("iPhone").map { it.name })
    }
}
