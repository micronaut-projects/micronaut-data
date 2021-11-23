
package example;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import io.micronaut.data.mongo.database.MongoDatabaseFactory;
import io.micronaut.transaction.SynchronousTransactionManager;
import jakarta.inject.Singleton;

@Singleton
public class ProductManager {

    private final ClientSession clientSession;
    private final MongoDatabaseFactory mongoDatabaseFactory;
    private final SynchronousTransactionManager<ClientSession> transactionManager;

    public ProductManager(ClientSession clientSession,
                          MongoDatabaseFactory mongoDatabaseFactory,
                          SynchronousTransactionManager<ClientSession> transactionManager) { // <1>
        this.clientSession = clientSession;
        this.mongoDatabaseFactory = mongoDatabaseFactory;
        this.transactionManager = transactionManager;
    }

    Product save(String name, Manufacturer manufacturer) {
        return transactionManager.executeWrite(status -> { // <2>
            final Product product = new Product(name, manufacturer);
            mongoDatabaseFactory.getDatabase(Product.class)
                    .getCollection("product", Product.class)
                    .insertOne(clientSession, product);
            return product;
        });
    }

    Product find(String name) {
        return transactionManager.executeRead(status -> { // <3>
            return mongoDatabaseFactory.getDatabase(Product.class)
                    .getCollection("product", Product.class)
                    .find(clientSession, Filters.eq("name", name)).first();
        });
    }
}
