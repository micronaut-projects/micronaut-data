/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.mongo.database;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.PersistentEntity;

/**
 * The simple Mongo database factory.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Experimental
public final class SimpleMongoDatabaseFactory implements MongoDatabaseFactory {

    private final MongoClient mongoClient;
    private final String databaseName;

    /**
     * Default constructor.
     *
     * @param mongoClient  The Mongo client
     * @param databaseName The default database name
     */
    public SimpleMongoDatabaseFactory(MongoClient mongoClient, String databaseName) {
        this.mongoClient = mongoClient;
        this.databaseName = databaseName;
    }

    @Override
    public MongoDatabase getDatabase(PersistentEntity persistentEntity) throws DataAccessException {
        return mongoClient.getDatabase(databaseName);
    }

    @Override
    public MongoDatabase getDatabase(Class<?> entityClass) throws DataAccessException {
        return mongoClient.getDatabase(databaseName);
    }
}
