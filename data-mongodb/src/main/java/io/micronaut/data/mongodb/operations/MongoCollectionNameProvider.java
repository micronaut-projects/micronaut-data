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
package io.micronaut.data.mongodb.operations;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.PersistentEntity;

/**
 * The MongoDB collection name provider.
 *
 * @since 3.9.0
 * @author Denis Stepanov
 */
@Experimental
public interface MongoCollectionNameProvider {

    /**
     * Provides the MongoDB collection name based on the {@link PersistentEntity}.
     * @param persistentEntity The persistent entity that is associated to the collection
     * @return The collection name
     */
    @NonNull
    String provide(@NonNull PersistentEntity persistentEntity);

}
