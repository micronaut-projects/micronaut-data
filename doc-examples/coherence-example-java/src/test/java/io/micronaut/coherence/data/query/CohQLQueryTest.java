/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.coherence.data.query;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test for {@link CohQLQueryBuilder}.
 */
class CohQLQueryTest {

    /**
     * CohQLQueryBuilder under test.
     */
    CohQLQueryBuilder builder;

    @BeforeEach
    void beforeEach() {
        builder = new CohQLQueryBuilder();
    }

    @Test
    void shouldThrowIfBuildingJoin() {
        assertThrows(UnsupportedOperationException.class, () -> builder.buildJoin(null, null, null, null, null, null));
    }

    @Test
    void shouldThrowIfResolvingJoin() {
        assertThrows(UnsupportedOperationException.class, () -> builder.resolveJoinType(null));
    }

    @Test
    void shouldReturnExpectedDeleteClause() {
        StringBuilder sb = new StringBuilder();
        builder.appendDeleteClause(sb);
        Assertions.assertEquals("DELETE  FROM ", sb.toString());
    }
}
