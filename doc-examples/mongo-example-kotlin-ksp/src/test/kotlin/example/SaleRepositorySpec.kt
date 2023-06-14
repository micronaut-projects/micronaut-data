package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.types.ObjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SaleRepositorySpec : AbstractMongoSpec() {

    @Inject
    lateinit var productRepository: ProductRepository

    @Inject
    lateinit var saleRepository: SaleRepository

    @Inject
    lateinit var manufacturerRepository: ManufacturerRepository

    @Test
    fun testReadWriteCustomType() {
        val apple = manufacturerRepository.save("Apple")
        val macBook = Product(ObjectId(),"MacBook", apple)
        productRepository.save(macBook)

        var sale = saleRepository.save(Sale(ObjectId(), macBook, Quantity(1)))

        assertNotNull(
                sale.id
        )
        assertEquals(1, sale.quantity.amount)

        sale = saleRepository.findById(sale.id!!).orElse(sale)
        assertNotNull(sale)
        assertEquals(1, sale.quantity.amount)
    }
}
