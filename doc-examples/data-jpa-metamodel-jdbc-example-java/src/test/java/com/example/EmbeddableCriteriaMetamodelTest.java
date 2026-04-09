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
import com.example.repository.specification.PurchaseOrderSpecification;
import io.micronaut.entities.EmbeddableClass;
import io.micronaut.entities.EmbeddedOwner;
import io.micronaut.entities.OrderPk;
import io.micronaut.entities.PurchaseOrder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
public class EmbeddableCriteriaMetamodelTest {

    final EmbeddedOwnerRepository embeddedOwnerRepository;
    final PurchaseOrderRepository purchaseOrderRepository;

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
        Assertions.assertEquals("B", result.getFirst().getOwnerName());
        Assertions.assertEquals("Y", result.getFirst().getEmbedded().embeddedName());
    }

    @Test
    void canQueryByEmbeddedIdParts_usingStaticMetamodel() {
        PurchaseOrder p1 = new PurchaseOrder();
        p1.setId(new OrderPk("t1", 1L));
        p1.setDescription("first");
        p1.setDetails(new EmbeddableClass("X", 10L, 1L, 1.5));

        PurchaseOrder p2 = new PurchaseOrder();
        p2.setId(new OrderPk("t1", 2L));
        p2.setDescription("second");
        p2.setDetails(new EmbeddableClass("X", 10L, 1L, 1.5));

        purchaseOrderRepository.saveAll(List.of(p1, p2));

        List<PurchaseOrder> result = purchaseOrderRepository.findAll(PurchaseOrderSpecification.tenantIdEquals("t1").and(PurchaseOrderSpecification.orderNoEquals(2L)));
        assertEquals(1, result.size());
        Assertions.assertEquals("second", result.getFirst().getDescription());
        Assertions.assertEquals("t1", result.getFirst().getId().tenantId());
        Assertions.assertEquals(2L, result.getFirst().getId().orderNo());
    }

    @Test
    void canQueryBySecondEmbeddedAlongsideEmbeddedId_usingStaticMetamodel() {
        PurchaseOrder p = new PurchaseOrder();
        p.setId(new OrderPk("t2", 10L));
        p.setDescription("has-details");
        p.setDetails(new EmbeddableClass("DETAILS", 7L, 3L, 9.9));

        purchaseOrderRepository.save(p);

        List<PurchaseOrder> orders = purchaseOrderRepository.findAll(PurchaseOrderSpecification.withEmbeddedName("DETAILS"));
        Assertions.assertEquals(10L, orders.getFirst().getId().orderNo());
    }

}
