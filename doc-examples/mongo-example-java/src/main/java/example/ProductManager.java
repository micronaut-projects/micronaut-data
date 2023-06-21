
package example;

import com.mongodb.client.ClientSession;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import io.micronaut.data.mongodb.operations.MongoDatabaseNameProvider;
import io.micronaut.data.mongodb.transaction.MongoTransactionOperations;
import jakarta.inject.Singleton;

@Singleton
public class ProductManager {

    private final ClientSession clientSession;
    private MongoClient mongoClient;
    private final MongoDatabaseNameProvider mongoDatabaseNameProvider;
    private final MongoTransactionOperations mongoTransactionOperations;

    public ProductManager(ClientSession clientSession,
                          MongoClient mongoClient,
                          MongoDatabaseNameProvider mongoDatabaseNameProvider,
                          MongoTransactionOperations mongoTransactionOperations) { // <1>
        this.clientSession = clientSession;
        this.mongoClient = mongoClient;
        this.mongoDatabaseNameProvider = mongoDatabaseNameProvider;
        this.mongoTransactionOperations = mongoTransactionOperations;
    }

    Product save(String name, Manufacturer manufacturer) {
        return mongoTransactionOperations.executeWrite(status -> { // <2>
            final Product product = new Product(name, manufacturer);
            mongoClient
                .getDatabase(mongoDatabaseNameProvider.provide(Product.class))
                .getCollection("product", Product.class)
                .insertOne(clientSession, product);
            return product;
        });
    }

    Product find(String name) {
        return mongoTransactionOperations.executeRead(status -> { // <3>
            return mongoClient
                .getDatabase(mongoDatabaseNameProvider.provide(Product.class))
                .getCollection("product", Product.class)
                .find(clientSession, Filters.eq("name", name)).first();
        });
    }
}
