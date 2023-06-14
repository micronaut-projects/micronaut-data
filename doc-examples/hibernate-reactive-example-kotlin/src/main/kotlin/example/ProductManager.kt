
package example

import io.micronaut.transaction.reactive.ReactiveTransactionStatus
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations
import jakarta.inject.Singleton
import org.hibernate.reactive.stage.Stage
import reactor.core.publisher.Mono

@Singleton
class ProductManager(
        private val transactionManager: ReactorReactiveTransactionOperations<Stage.Session>
) // <1>
{

    fun save(name: String, manufacturer: Manufacturer): Mono<Product> {
        return transactionManager.withTransactionMono { status: ReactiveTransactionStatus<Stage.Session> -> // <2>
            val product = Product(null, name, manufacturer)
            Mono.fromCompletionStage {
                status.connection.persist(product)
            }.thenReturn(product)
        }
    }

    fun find(name: String): Mono<Product> {
        return transactionManager.withTransactionMono { status: ReactiveTransactionStatus<Stage.Session> -> // <3>
            Mono.fromCompletionStage(
                status.connection.createQuery(
                    "from Product p where p.name = :name",
                    Product::class.java
                )
                    .setParameter("name", name)
                    .singleResult
            )
        }
    }
}
