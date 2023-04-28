package io.micronaut.data.mongodb.transaction;

import com.mongodb.client.ClientSession;
import io.micronaut.transaction.TransactionOperations;

public interface MongoTransactionOperations extends TransactionOperations<ClientSession> {
}
