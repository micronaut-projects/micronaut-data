/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.hibernate;

import io.micronaut.data.tck.tests.AbstractJakartaDataTest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@H2DBProperties
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@MicronautTest(transactional = false)
public class HibernateJakartaDataTest extends AbstractJakartaDataTest {

    @Override
    protected boolean supportsCursorPaginationWithRestrictions() {
        return false;
    }

    // Enable after https://github.com/jakartaee/data/issues/1290

    public void testRuntimeRestrictionsWithLength() {
    }

    public void testRuntimeRestrictionsWithLengthGreaterThan() {
    }

    public void testRuntimeRestrictionsWithLikeCustomWildcardsAndEscape() {
    }

    public void testRuntimeRestrictionsWithNotLikeCustomWildcardsAndEscape() {
    }

    public void testAbs() {
    }

    public void testRuntimeRestrictionsWithNumericNegated() {
    }

    public void testRuntimeRestrictionsWithNumericAbsOnEmbedded() {
    }

}
