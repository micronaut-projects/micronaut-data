
package example

import io.micronaut.data.hibernate.reactive.operations.HibernateReactorRepositoryOperations
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import jakarta.inject.Singleton
import org.hibernate.reactive.stage.Stage
import reactor.core.publisher.Mono

@Singleton
class ProductManager {

    private final ReactorReactiveTransactionOperations<Stage.Session> operations

    ProductManager(ReactorReactiveTransactionOperations<Stage.Session> operations) {
        this.operations = operations
    }

    Mono<Product> save(String name, Manufacturer manufacturer) {
        return operations.withTransactionMono(tx -> {
            final Product product = new Product(name, manufacturer)
            return Mono.fromCompletionStage(() -> tx.getConnection().persist(product)).thenReturn(product)
        })
    }

    Mono<Product> find(String name) {
        return operations.withTransactionMono(TransactionDefinition.READ_ONLY, status ->
                Mono.fromCompletionStage(status.getConnection().createQuery("from Product p where p.name = :name", Product.class)
                .setParameter("name", name)
                .getSingleResult())
        )
    }
}
