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
package io.micronaut.data.cosmos.common;

/**
 * The constants needed for Cosmos DB implementation.
 */
public final class Constants {

    /**
     * The name for PartitionKey role as custom Cosmos repository method parameter.
     */
    public static final String PARTITION_KEY_ROLE = "partitionKey";

    /**
     * Each Cosmos document will have id property that will be identifier. Even if it does not match with our entity
     * id this field will be present in the document and is needed for update/delete operations.
     */
    public static final String INTERNAL_ID = "id";

    /**
     * This value will be used as partition key if none is defined on the entity.
     */
    public static final String NO_PARTITION_KEY = "/null";

    private Constants() { }
}
