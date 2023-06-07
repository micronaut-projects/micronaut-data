package example

import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import org.hibernate.Session

@Singleton
class ProductManager {

    private final EntityManager entityManager
    private final TransactionOperations<Session> transactionManager

    ProductManager(EntityManager entityManager,
                   TransactionOperations<Session> transactionManager) { // <1>
        this.entityManager = entityManager
        this.transactionManager = transactionManager
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite { // <2>
            Product product = new Product(name, manufacturer)
            entityManager.persist(product)
            return product
        }
    }

    Product find(String name) {
        return transactionManager.executeRead { status -> // <3>
            status.getConnection().createQuery("from Product p where p.name = :name", Product)
                    .setParameter("name", name)
                    .singleResult
        }
    }
}
