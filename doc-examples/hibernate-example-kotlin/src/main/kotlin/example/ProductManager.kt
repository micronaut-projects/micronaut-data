package example

import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import org.hibernate.Session

@Singleton
class ProductManager(
    private val entityManager: EntityManager,
    private val transactionManager: TransactionOperations<Session> // <1>
) {

    fun save(name: String, manufacturer: Manufacturer): Product {
        return transactionManager.executeWrite { // <2>
            val product = Product(null, name, manufacturer)
            entityManager.persist(product)
            product
        }
    }

    fun find(name: String): Product {
        return transactionManager.executeRead { status ->  // <3>
            status.connection.createQuery("from Product p where p.name = :name", Product::class.java)
                .setParameter("name", name)
                .singleResult
        }
    }
}
