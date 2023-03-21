
package example

import io.micronaut.transaction.SynchronousTransactionManager
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.sql.Connection

@Singleton
class ProductManager {

    private final EntityManager entityManager
    private final SynchronousTransactionManager<Connection> transactionManager

    ProductManager(
            EntityManager entityManager,
            SynchronousTransactionManager<Connection> transactionManager) { // <1>
        this.entityManager = entityManager
        this.transactionManager = transactionManager
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite { // <2>
            final product = new Product(name, manufacturer)
            entityManager.persist(product)
            return product
        }
    }

    Product find(String name) {
        return transactionManager.executeRead { // <3>
            entityManager.createQuery("from Product p where p.name = :name", Product)
                    .setParameter("name", name)
                    .singleResult
        }
    }
}
