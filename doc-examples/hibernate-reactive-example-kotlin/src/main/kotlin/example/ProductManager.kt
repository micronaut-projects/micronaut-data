
package example

import io.micronaut.transaction.SynchronousTransactionManager
import java.sql.Connection
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager

@Singleton
class ProductManager(
        private val entityManager: EntityManager,
        private val transactionManager: SynchronousTransactionManager<Connection>) // <1>
{

    fun save(name: String, manufacturer: Manufacturer): Product {
        return transactionManager.executeWrite { // <2>
            val product = Product(0, name, manufacturer)
            entityManager.persist(product)
            product
        }
    }

    fun find(name: String): Product {
        return transactionManager.executeRead {  // <3>
            entityManager.createQuery("from Product p where p.name = :name", Product::class.java)
                    .setParameter("name", name)
                    .singleResult
        }
    }
}
