
package example;

import io.micronaut.transaction.SynchronousTransactionManager;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import java.sql.Connection;

@Singleton
public class ProductManager {

    private final EntityManager entityManager;
    private final SynchronousTransactionManager<Connection> transactionManager;

    public ProductManager(
            EntityManager entityManager,
            SynchronousTransactionManager<Connection> transactionManager) { // <1>
        this.entityManager = entityManager;
        this.transactionManager = transactionManager;
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite(status -> { // <2>
            final Product product = new Product(name, manufacturer);
            entityManager.persist(product);
            return product;
        });
    }

    Product find(String name) {
        return transactionManager.executeRead(status -> // <3>
                entityManager.createQuery("from Product p where p.name = :name", Product.class)
                    .setParameter("name", name)
                    .getSingleResult()
        );
    }
}
