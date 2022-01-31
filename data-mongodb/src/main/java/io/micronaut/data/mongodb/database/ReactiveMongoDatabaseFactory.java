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
package io.micronaut.data.mongodb.database;

import com.mongodb.reactivestreams.client.MongoDatabase;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.data.model.PersistentEntity;

/**
 * Mongo reactive database factory.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Experimental
public interface ReactiveMongoDatabaseFactory {

    /**
     * The Mongo database factory.
     *
     * @param persistentEntity The persistent entity
     * @return The Mongo database
     */
    MongoDatabase getDatabase(PersistentEntity persistentEntity);

    /**
     * The Mongo database factory.
     *
     * @param entityClass The entity class
     * @return The Mongo database
     */
    MongoDatabase getDatabase(Class<?> entityClass);

}
