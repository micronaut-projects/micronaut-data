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
package com.example;

import com.example.repository.EmbeddedOwnerRepository;
import com.example.repository.PurchaseOrderRepository;
import com.example.repository.specification.EmbeddedOwnerSpecification;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static com.example.repository.specification.PurchaseOrderSpecification.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class EmbeddableCriteriaMetamodelTest {

    final EmbeddedOwnerRepository embeddedOwnerRepository;
    final PurchaseOrderRepository purchaseOrderRepository;
    @Inject
    DataSource dataSource;

    public EmbeddableCriteriaMetamodelTest(EmbeddedOwnerRepository embeddedOwnerRepository,
                                           PurchaseOrderRepository purchaseOrderRepository) {
        this.embeddedOwnerRepository = embeddedOwnerRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @BeforeEach
    void cleanup() {
        embeddedOwnerRepository.deleteAll();
        purchaseOrderRepository.deleteAll();
    }

    @Test
    void canQueryByEmbeddedAttribute_usingStaticMetamodel() {
        EmbeddedOwner a = new EmbeddedOwner();
        a.setOwnerName("A");
        a.setEmbedded(new EmbeddableClass("X", 10L, 1L, 1.5));

        EmbeddedOwner b = new EmbeddedOwner();
        b.setOwnerName("B");
        b.setEmbedded(new EmbeddableClass("Y", 99L, 2L, 2.5));
        embeddedOwnerRepository.saveAll(List.of(a, b));

        List<EmbeddedOwner> result = embeddedOwnerRepository.findAll(EmbeddedOwnerSpecification.withEmbeddedName("Y"));
        assertEquals(1, result.size());
        assertEquals("B", result.getFirst().getOwnerName());
        assertEquals("Y", result.getFirst().getEmbedded().getEmbeddedName());
    }

    @Test
    void canQueryByEmbeddedIdParts_usingStaticMetamodel() {
        PurchaseOrder p1 = new PurchaseOrder();
        p1.setId(new OrderPk("t1", 1L));
        p1.setDescription("first");

        PurchaseOrder p2 = new PurchaseOrder();
        p2.setId(new OrderPk("t1", 2L));
        p2.setDescription("second");

        purchaseOrderRepository.saveAll(List.of(p1, p2));

        List<PurchaseOrder> result = purchaseOrderRepository.findAll(tenantIdEquals("t1").and(orderNoEquals(2L)));
        assertEquals(1, result.size());
        assertEquals("second", result.getFirst().getDescription());
        assertEquals("t1", result.getFirst().getId().getTenantId());
        assertEquals(2L, result.getFirst().getId().getOrderNo());
    }

    @Test
    void canQueryBySecondEmbeddedAlongsideEmbeddedId_usingStaticMetamodel() {
        PurchaseOrder p = new PurchaseOrder();
        p.setId(new OrderPk("t2", 10L));
        p.setDescription("has-details");
        p.setDetails(new EmbeddableClass("DETAILS", 7L, 3L, 9.9));

        purchaseOrderRepository.save(p);

        List<PurchaseOrder> orders = purchaseOrderRepository.findAll(withEmbeddedName("DETAILS"));
        assertEquals(10L, orders.getFirst().getId().getOrderNo());
    }

    @Test
    void generatedMetamodelHasExpectedFields_embeddedOwner_and_purchaseOrder() throws Exception {
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
    void generatedMetamodelHasExpectedFields_embeddables_optionalIfYouGenerateThem() throws Exception {
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

        MetamodelAssertions.assertClassFieldIsEntityType(EmbeddableClass_.class, EmbeddableType.class, EmbeddableClass.class);
    }
}
