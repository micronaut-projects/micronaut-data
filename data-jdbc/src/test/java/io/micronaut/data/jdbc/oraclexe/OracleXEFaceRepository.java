/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.jdbc.oraclexe;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Face;
import io.micronaut.data.tck.repositories.FaceRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface OracleXEFaceRepository extends FaceRepository {

    @Query(
        """
        SELECT * FROM face f WHERE
            (f.date_created >= COALESCE(TO_TIMESTAMP(:dateCreatedParam, 'YYYY-MM-DD"T"HH24\\:MI\\:SS"Z"'), f.date_created) OR :dateCreatedParam IS NULL) AND
            (f.name = :name OR :name IS NULL)
        """)
    List<Face> findAllWithOptionalFilters(
        @Nullable String name,
        @Nullable String dateCreatedParam);

    /**
     * Test for custom repository void method not returning result.
     */
    @Query("LOCK TABLE face IN EXCLUSIVE MODE")
    void lock();
}
