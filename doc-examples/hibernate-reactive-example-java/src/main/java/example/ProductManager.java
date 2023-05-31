
package example;

import io.micronaut.data.connection.manager.reactive.ReactorReactiveConnectionOperations;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import jakarta.inject.Singleton;
import org.hibernate.reactive.stage.Stage;
import reactor.core.publisher.Mono;

@Singleton
public class ProductManager {

    private final ReactorReactiveConnectionOperations<Stage.Session> connectionOperations;
    private final ReactorReactiveTransactionOperations<Stage.Session> transactionOperations;

    public ProductManager(ReactorReactiveConnectionOperations<Stage.Session> connectionOperations,
                          ReactorReactiveTransactionOperations<Stage.Session> transactionOperations) {
        this.connectionOperations = connectionOperations;
        this.transactionOperations = transactionOperations;
    }

    Mono<Product> save(String name, Manufacturer manufacturer) {
        return transactionOperations.withTransactionMono(status -> {
            final Product product = new Product(name, manufacturer);
            return Mono.fromCompletionStage(() -> status.getConnection().persist(product)).thenReturn(product);
        });
    }

    Mono<Product> find(String name) {
        return connectionOperations.withConnectionMono(status -> Mono.fromCompletionStage(status.getConnection().createQuery("from Product p where p.name = :name", Product.class)
                .setParameter("name", name)
                .getSingleResult())
        );
    }
}
