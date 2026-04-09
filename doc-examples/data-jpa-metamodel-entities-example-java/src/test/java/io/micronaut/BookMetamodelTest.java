/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package io.micronaut;

import io.micronaut.entities.Book;
import io.micronaut.entities.Book_;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import static io.micronaut.MetamodelAssertionsUtils.assertMetaModelClassIsAnnotatedCorrectly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class BookMetamodelTest {

    @Test
    void generatedMetamodelHasExpectedFields() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(Book_.class, Book.class);

        assertNotNull(Book_.class.getDeclaredField("id"));
        assertNotNull(Book_.class.getDeclaredField("title"));
        assertNotNull(Book_.class.getDeclaredField("pages"));
        assertNotNull(Book_.class.getDeclaredField("category"));

        assertEquals(SingularAttribute.class.getName(), Book_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Book_.class.getDeclaredField("title").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Book_.class.getDeclaredField("pages").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Book_.class.getDeclaredField("category").getType().getName());

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(Book_.class, EntityType.class, Book.class);
    }
}
