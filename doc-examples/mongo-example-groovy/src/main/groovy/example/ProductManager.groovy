
package example

import com.mongodb.client.ClientSession
import com.mongodb.client.model.Filters
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider
import io.micronaut.data.mongodb.transaction.MongoSynchronousTransactionManager
import jakarta.inject.Singleton

@Singleton
class ProductManager {
    private final ClientSession clientSession
    private final MongoDatabaseNameProvider mongoDatabaseNameProvider;
    private final MongoSynchronousTransactionManager transactionManager;

    ProductManager(ClientSession clientSession,
                   MongoDatabaseNameProvider mongoDatabaseNameProvider,
                   MongoSynchronousTransactionManager transactionManager) {
        this.clientSession = clientSession
        this.mongoDatabaseNameProvider = mongoDatabaseNameProvider
        this.transactionManager = transactionManager
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite(status -> { // <2>
            final Product product = new Product(name, manufacturer)
            transactionManager.getClient()
                    .getDatabase(mongoDatabaseNameProvider.provide(Product.class))
                    .getCollection("product", Product.class)
                    .insertOne(clientSession, product)
            return product
        })
    }

    Product find(String name) {
        return transactionManager.executeRead(status -> { // <3>
            return transactionManager.getClient()
                    .getDatabase(mongoDatabaseNameProvider.provide(Product.class))
                    .getCollection("product", Product.class)
                    .find(clientSession, Filters.eq("name", name)).first()
        })
    }
}
