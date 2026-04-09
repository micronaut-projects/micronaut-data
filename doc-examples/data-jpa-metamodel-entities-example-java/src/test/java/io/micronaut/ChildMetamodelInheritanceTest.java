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


import io.micronaut.entities.Child;
import io.micronaut.entities.Child_;
import io.micronaut.entities.Parent;
import io.micronaut.entities.Parent_;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.MappedSuperclassType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import static io.micronaut.MetamodelAssertionsUtils.assertMetaModelClassIsAnnotatedCorrectly;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class ChildMetamodelInheritanceTest {

    @Test
    void generatedMetamodelHasExpectedFields_includingInheritedFromMappedSuperclass() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(Child_.class, Child.class);

        assertNotNull(Child_.class.getField("id"));
        assertNotNull(Child_.class.getField("name"));
        assertNotNull(Child_.class.getDeclaredField("age"));

        assertEquals(SingularAttribute.class.getName(), Child_.class.getField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Child_.class.getField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Child_.class.getDeclaredField("age").getType().getName());

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(Child_.class, EntityType.class, Child.class);
    }

    @Test
    void mappedSuperclassMetamodel_optional() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(Parent_.class, Parent.class);

        assertNotNull(Parent_.class.getDeclaredField("id"));
        assertNotNull(Parent_.class.getDeclaredField("name"));

        assertEquals(SingularAttribute.class.getName(),
            Parent_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            Parent_.class.getDeclaredField("name").getType().getName());

        assertThrows(NoSuchFieldException.class, () -> Parent_.class.getDeclaredField("age"));
        MetamodelAssertionsUtils.assertClassFieldIsEntityType(Parent_.class, MappedSuperclassType.class, Parent.class);
    }
}
