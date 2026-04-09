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

import io.micronaut.entities.Category;
import io.micronaut.entities.Category_;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import static io.micronaut.MetamodelAssertionsUtils.assertMetaModelClassIsAnnotatedCorrectly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class CategoryMetamodelTest {

    @Test
    void generatedMetamodelHasExpectedFields() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(Category_.class, Category.class);

        assertNotNull(Category_.class.getDeclaredField("id"));
        assertNotNull(Category_.class.getDeclaredField("name"));
        assertNotNull(Category_.class.getDeclaredField("books"));

        assertEquals(SingularAttribute.class.getName(), Category_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Category_.class.getDeclaredField("name").getType().getName());
        assertEquals(ListAttribute.class.getName(), Category_.class.getDeclaredField("books").getType().getName());

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(Category_.class, EntityType.class, Category.class);
    }

}
