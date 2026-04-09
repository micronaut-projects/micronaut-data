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

import io.micronaut.entities.*;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import static io.micronaut.MetamodelAssertionsUtils.assertMetaModelClassIsAnnotatedCorrectly;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class AccessTypeMetamodelTest {

    @Test
    void generatedMetamodelHasExpectedFields_fieldAccess() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(EmployeeFieldAccess_.class, EmployeeFieldAccess.class);

        assertNotNull(EmployeeFieldAccess_.class.getDeclaredField("id"));
        assertNotNull(EmployeeFieldAccess_.class.getDeclaredField("name"));
        assertNotNull(EmployeeFieldAccess_.class.getDeclaredField("salary"));
        assertNotNull(EmployeeFieldAccess_.class.getDeclaredField("class_"));


        assertEquals(SingularAttribute.class.getName(),
            EmployeeFieldAccess_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeFieldAccess_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeFieldAccess_.class.getDeclaredField("salary").getType().getName());

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(EmployeeFieldAccess_.class, EntityType.class, EmployeeFieldAccess.class);
    }

    @Test
    void generatedMetamodelHasExpectedFields_propertyAccess() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(EmployeePropertyAccess_.class, EmployeePropertyAccess.class);

        assertNotNull(EmployeePropertyAccess_.class.getDeclaredField("id"));
        assertNotNull(EmployeePropertyAccess_.class.getDeclaredField("name"));
        assertNotNull(EmployeePropertyAccess_.class.getDeclaredField("salary"));

        assertEquals(SingularAttribute.class.getName(),
            EmployeePropertyAccess_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeePropertyAccess_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeePropertyAccess_.class.getDeclaredField("salary").getType().getName());

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(EmployeePropertyAccess_.class, EntityType.class, EmployeePropertyAccess.class);

    }

    @Test
    void generatedMetamodelHasExpectedFields_mixedAccess_andDoesNotContainUnmappedField() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(EmployeeMixedAccess_.class, EmployeeMixedAccess.class);

        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("id"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("name"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("salary"));
        assertNotNull(EmployeeMixedAccess_.class.getDeclaredField("fieldAnnotated"));

        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("salary").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccess_.class.getDeclaredField("fieldAnnotated").getType().getName());

        assertThrows(NoSuchFieldException.class,
            () -> EmployeeMixedAccess_.class.getDeclaredField("fieldWithoutAccessors"));

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(EmployeeMixedAccess_.class, EntityType.class, EmployeeMixedAccess.class);

    }

    @Test
    void generatedMetamodelHasExpectedFields_mixedAccessEmbeddableId_andDoesNotContainUnmappedField() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(EmployeeMixedAccessEmbeddedId_.class, EmployeeMixedAccessEmbeddedId.class);

        assertNotNull(EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("id"));
        assertNotNull(EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("name"));
        assertNotNull(EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("salary"));
        assertNotNull(EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("fieldAnnotated"));

        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("salary").getType().getName());
        assertEquals(SingularAttribute.class.getName(),
            EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("fieldAnnotated").getType().getName());

        assertThrows(NoSuchFieldException.class,
            () -> EmployeeMixedAccessEmbeddedId_.class.getDeclaredField("fieldWithoutAccessors"));

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(EmployeeMixedAccessEmbeddedId_.class, EntityType.class, EmployeeMixedAccessEmbeddedId.class);
    }
}
