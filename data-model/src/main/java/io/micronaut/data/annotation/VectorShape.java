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

import io.micronaut.core.annotation.Experimental;

/**
 * Declares how vector values are physically represented for persistence and database operations.
 *
 * <p>Use this with {@link VectorStorage#shape()} to align mapping and index configuration with the
 * capabilities of the target database.</p>
 *
 * <p>See:</p>
 * <ul>
 *     <li><a href="https://docs.oracle.com/en/database/oracle/oracle-database/26/vecse/create-tables-using-vector-data-type.html">Oracle AI Vector Search: VECTOR data type</a></li>
 *     <li><a href="https://dev.mysql.com/doc/refman/9.6/en/vector.html">MySQL VECTOR type reference</a></li>
 * </ul>
 *
 * @since 5.0.0
 */
@Experimental
public enum VectorShape {
    /**
     * Dense vectors store every dimension explicitly.
     */
    DENSE,
    /**
     * Sparse vectors store only non-zero dimensions with their positions.
     */
    SPARSE
}
