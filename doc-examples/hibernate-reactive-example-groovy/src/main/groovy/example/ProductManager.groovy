
package example

import io.micronaut.data.hibernate.reactive.operations.HibernateReactorRepositoryOperations
import jakarta.inject.Singleton
import reactor.core.publisher.Mono

@Singleton
class ProductManager {

    private final HibernateReactorRepositoryOperations operations

    ProductManager(HibernateReactorRepositoryOperations operations) {
        this.operations = operations
    }

    Mono<Product> save(String name, Manufacturer manufacturer) {
        return operations.withTransactionMono(tx -> {
            final Product product = new Product(name, manufacturer)
            return Mono.fromCompletionStage(() -> tx.getConnection().persist(product)).thenReturn(product)
        })
    }

    Mono<Product> find(String name) {
        return operations.withSession(session ->
                Mono.fromCompletionStage(session.createQuery("from Product p where p.name = :name", Product.class)
                .setParameter("name", name)
                .getSingleResult())
        )
    }
}
