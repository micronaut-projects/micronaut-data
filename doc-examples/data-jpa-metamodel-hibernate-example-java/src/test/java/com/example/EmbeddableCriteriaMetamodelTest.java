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
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.EmbeddableType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
public class EmbeddableCriteriaMetamodelTest {

    final EmbeddedOwnerRepository embeddedOwnerRepository;
    final PurchaseOrderRepository purchaseOrderRepository;
    final EntityManager entityManager;

    public EmbeddableCriteriaMetamodelTest(EmbeddedOwnerRepository embeddedOwnerRepository,
                                           PurchaseOrderRepository purchaseOrderRepository,
                                           EntityManager entityManager) {
        this.embeddedOwnerRepository = embeddedOwnerRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.entityManager = entityManager;
    }

    @Test
    void canQueryByEmbeddedAttribute_usingStaticMetamodel() {
        EmbeddedOwner a = new EmbeddedOwner();
        a.setOwnerName("A");
        a.setEmbedded(new EmbeddableClass("X", 10L, 1L, 1.5));

        EmbeddedOwner b = new EmbeddedOwner();
        b.setOwnerName("B");
        b.setEmbedded(new EmbeddableClass("Y", 99L, 2L, 2.5));

        embeddedOwnerRepository.save(a);
        embeddedOwnerRepository.save(b);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<EmbeddedOwner> cq = cb.createQuery(EmbeddedOwner.class);
        Root<EmbeddedOwner> root = cq.from(EmbeddedOwner.class);

        cq.select(root)
            .where(cb.equal(
                root.get(EmbeddedOwner_.embedded).get(EmbeddableClass_.embeddedName),
                "Y"
            ));

        List<EmbeddedOwner> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("B", result.get(0).getOwnerName());
        assertEquals("Y", result.get(0).getEmbedded().getEmbeddedName());
    }

    @Test
    void canQueryByEmbeddedIdParts_usingStaticMetamodel() {
        PurchaseOrder p1 = new PurchaseOrder();
        p1.setId(new OrderPk("t1", 1L));
        p1.setDescription("first");
        p1.setDetails(new EmbeddableClass("D1", 1L, 1L, 1.1));

        PurchaseOrder p2 = new PurchaseOrder();
        p2.setId(new OrderPk("t1", 2L));
        p2.setDescription("second");
        p2.setDetails(new EmbeddableClass("D2", 2L, 2L, 2.2));

        purchaseOrderRepository.save(p1);
        purchaseOrderRepository.save(p2);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<PurchaseOrder> cq = cb.createQuery(PurchaseOrder.class);
        Root<PurchaseOrder> root = cq.from(PurchaseOrder.class);

        cq.select(root)
            .where(cb.and(
                cb.equal(root.get(PurchaseOrder_.id).get(OrderPk_.tenantId), "t1"),
                cb.equal(root.get(PurchaseOrder_.id).get(OrderPk_.orderNo), 2L)
            ));

        List<PurchaseOrder> result = entityManager.createQuery(cq).getResultList();
        assertEquals(1, result.size());
        assertEquals("second", result.get(0).getDescription());
        assertEquals("t1", result.get(0).getId().getTenantId());
        assertEquals(2L, result.get(0).getId().getOrderNo());
    }

    @Test
    void canQueryBySecondEmbeddedAlongsideEmbeddedId_usingStaticMetamodel() {
        PurchaseOrder p = new PurchaseOrder();
        p.setId(new OrderPk("t2", 10L));
        p.setDescription("has-details");
        p.setDetails(new EmbeddableClass("DETAILS", 7L, 3L, 9.9));

        purchaseOrderRepository.save(p);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<PurchaseOrder> root = cq.from(PurchaseOrder.class);

        cq.select(root.get(PurchaseOrder_.id).get(OrderPk_.orderNo))
            .where(cb.equal(
                root.get(PurchaseOrder_.details).get(EmbeddableClass_.embeddedName),
                "DETAILS"
            ));

        List<Long> orderNos = entityManager.createQuery(cq).getResultList();
        assertEquals(List.of(10L), orderNos);
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
