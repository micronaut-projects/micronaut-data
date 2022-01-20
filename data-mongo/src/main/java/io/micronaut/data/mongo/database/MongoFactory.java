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

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import io.micronaut.configuration.mongo.core.DefaultMongoConfiguration;
import io.micronaut.configuration.mongo.core.NamedMongoConfiguration;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;

@Internal
@Factory
final class MongoFactory {

    @Primary
    @Singleton
    MongoDatabaseFactory primaryMongoDatabaseFactory(DefaultMongoConfiguration mongoConfiguration, @Primary MongoClient mongoClient) {
        return mongoConfiguration.getConnectionString()
                .map(ConnectionString::getDatabase)
                .filter(StringUtils::isNotEmpty)
                .map(databaseName -> new SimpleMongoDatabaseFactory(mongoClient, databaseName))
                .orElseThrow(() -> new ConfigurationException("Please specify the default Mongo database in the url string"));
    }

    @EachBean(NamedMongoConfiguration.class)
    @Singleton
    MongoDatabaseFactory namedMongoDatabaseFactory(NamedMongoConfiguration mongoConfiguration, @Parameter MongoClient mongoClient) {
        return mongoConfiguration.getConnectionString()
                .map(ConnectionString::getDatabase)
                .filter(StringUtils::isNotEmpty)
                .map(databaseName -> new SimpleMongoDatabaseFactory(mongoClient, databaseName))
                .orElseThrow(() -> new ConfigurationException("Please specify the default Mongo database in the url string"));
    }

}
