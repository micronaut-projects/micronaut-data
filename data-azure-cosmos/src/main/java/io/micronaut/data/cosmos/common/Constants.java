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
     * _etag or default version field name in Cosmos DB.
     */
    public static final String ETAG_PROPERTY_DEFAULT_NAME = "_etag";

    /**
     * The name for PartitionKey role as custom Cosmos repository method parameter.
     */
    public static final String PARTITION_KEY_ROLE = "partitionKey";

    private Constants() { }
}
