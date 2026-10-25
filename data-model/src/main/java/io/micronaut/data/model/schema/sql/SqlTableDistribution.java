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
package io.micronaut.data.model.schema.sql;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How a table is distributed across the shards of an Oracle Globally Distributed Database.
 *
 * @param type          The distribution type
 * @param keyColumns    The sharding key columns of a sharded table, empty for a duplicated table
 * @param parent        The table mapping of the root table of the table family, if a sharded table is a child table
 * @param tablespace    The tablespace set of a sharded table, or the tablespace of a duplicated table, if any
 *
 * @since 5.3
 */
@Internal
public record SqlTableDistribution(Type type,
                                   List<String> keyColumns,
                                   @Nullable SqlTableMapping parent,
                                   @Nullable String tablespace) {

    /**
     * The distribution type.
     */
    public enum Type {
        /**
         * Partitioned across the shards by consistent hash of the sharding key.
         */
        SHARDED,
        /**
         * Replicated to every shard.
         */
        DUPLICATED
    }
}
