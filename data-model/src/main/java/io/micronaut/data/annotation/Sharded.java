/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates the table of an entity as an Oracle sharded table, partitioned across the shards of an Oracle Globally
 * Distributed Database by consistent hash of its sharding key ({@code CREATE SHARDED TABLE ... PARTITION BY CONSISTENT
 * HASH ... PARTITIONS AUTO}).
 *
 * <p>The sharding key must be part of the primary key. A table with a {@link #parent()} belongs to the table family of
 * the parent and is stored in the same shard as the parent rows with the same sharding key ({@code PARENT}). Other
 * dialects generate a plain table.</p>
 *
 * @since 5.3
 * @see Duplicated
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Sharded {

    /**
     * @return The properties of the sharding key, with dots for the properties of an embedded id, for example
     * {@code id.customerId}. Defaults to the primary key, or to the sharding key of the {@link #parent()}
     */
    String[] key() default {};

    /**
     * @return The root entity of the table family, if this table is a child table of the family
     */
    Class<?> parent() default void.class;

    /**
     * @return The tablespace set that stores the partitions ({@code TABLESPACE SET}), required unless the shard catalog
     * has a default tablespace set
     */
    String tablespaceSet() default "";
}
