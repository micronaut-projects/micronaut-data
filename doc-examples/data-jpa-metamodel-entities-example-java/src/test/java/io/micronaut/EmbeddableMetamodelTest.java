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
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import static io.micronaut.MetamodelAssertionsUtils.assertMetaModelClassIsAnnotatedCorrectly;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class EmbeddableMetamodelTest {

    @Test
    void generatedMetamodelHasExpectedFields_embeddedOwner_and_purchaseOrder() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(EmbeddedOwner_.class, EmbeddedOwner.class);

        assertNotNull(EmbeddedOwner_.class.getDeclaredField("id"));
        assertNotNull(EmbeddedOwner_.class.getDeclaredField("ownerName"));
        assertNotNull(EmbeddedOwner_.class.getDeclaredField("embedded"));

        assertEquals(SingularAttribute.class.getName(), EmbeddedOwner_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), EmbeddedOwner_.class.getDeclaredField("ownerName").getType().getName());
        assertEquals(SingularAttribute.class.getName(), EmbeddedOwner_.class.getDeclaredField("embedded").getType().getName());

        assertNotNull(PurchaseOrder_.class.getDeclaredField("id"));
        assertNotNull(PurchaseOrder_.class.getDeclaredField("description"));
        assertNotNull(PurchaseOrder_.class.getDeclaredField("details"));

        assertEquals(SingularAttribute.class.getName(), PurchaseOrder_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), PurchaseOrder_.class.getDeclaredField("description").getType().getName());
        assertEquals(SingularAttribute.class.getName(), PurchaseOrder_.class.getDeclaredField("details").getType().getName());
    }

    @Test
    void generatedMetamodelHasExpectedFields_embeddable_optionalIfYouGenerateThem() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(EmbeddableClass_.class, EmbeddableClass.class);

        assertNotNull(EmbeddableClass_.class.getDeclaredField("embeddedName"));
        assertNotNull(EmbeddableClass_.class.getDeclaredField("number"));
        assertNotNull(EmbeddableClass_.class.getDeclaredField("n"));
        assertNotNull(EmbeddableClass_.class.getDeclaredField("d"));

        assertEquals(SingularAttribute.class.getName(), EmbeddableClass_.class.getDeclaredField("embeddedName").getType().getName());
        assertEquals(SingularAttribute.class.getName(), EmbeddableClass_.class.getDeclaredField("number").getType().getName());
        assertEquals(SingularAttribute.class.getName(), EmbeddableClass_.class.getDeclaredField("n").getType().getName());
        assertEquals(SingularAttribute.class.getName(), EmbeddableClass_.class.getDeclaredField("d").getType().getName());

        assertNotNull(OrderPk_.class.getDeclaredField("tenantId"));
        assertNotNull(OrderPk_.class.getDeclaredField("orderNo"));
        assertEquals(SingularAttribute.class.getName(), OrderPk_.class.getDeclaredField("tenantId").getType().getName());
        assertEquals(SingularAttribute.class.getName(), OrderPk_.class.getDeclaredField("orderNo").getType().getName());

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(EmbeddableClass_.class, EmbeddableType.class, EmbeddableClass.class);
    }
}
