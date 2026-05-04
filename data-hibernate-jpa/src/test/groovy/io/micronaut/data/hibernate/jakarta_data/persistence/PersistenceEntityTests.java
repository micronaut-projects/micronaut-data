package io.micronaut.data.hibernate.jakarta_data.persistence;

import io.micronaut.context.annotation.Property;
import io.micronaut.data.hibernate.H2DBProperties;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.exceptions.EntityExistsException;
import jakarta.data.exceptions.OptimisticLockingFailureException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.micronaut.data.hibernate.jakarta_data.persistence.CatalogProduct.Department.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Execute tests with a Persistence specific entity with a repository that requires read and writes (AKA not read-only)
 */
//@Property(name = "jpa.default.properties.hibernate.show_sql", value = "true")
@Property(name = "jpa.default.properties.uniqueResultOnFindOne", value = "true")
@H2DBProperties
@MicronautTest(transactional = false)
public class PersistenceEntityTests {

    @Inject
    Catalog catalog;

    @Test
    public void testEntityManager() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        catalog.customSave(CatalogProduct.of("bicycle", 359.98, "TEST-PROD-81", CatalogProduct.Department.SPORTING_GOODS));
        catalog.customSave(CatalogProduct.of("shin guards", 8.99, "TEST-PROD-83", CatalogProduct.Department.SPORTING_GOODS));
        catalog.customSave(CatalogProduct.of("dishwasher", 788.10, "TEST-PROD-86", CatalogProduct.Department.APPLIANCES));
        catalog.customSave(CatalogProduct.of("socks", 5.99, "TEST-PROD-87", CatalogProduct.Department.CLOTHING));
        catalog.customSave(CatalogProduct.of("volleyball", 10.99, "TEST-PROD-89", CatalogProduct.Department.SPORTING_GOODS));

//        assertEquals(385.95, catalog.sumPrices(Department.CLOTHING, Department.SPORTING_GOODS), 0.001);
//        assertEquals(794.09, catalog.sumPrices(Department.CLOTHING, Department.APPLIANCES), 0.001);
    }

    @Test
    public void testIdAttributeWithDifferentName() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        catalog.customSave(CatalogProduct.of("apple", 1.19, "TEST-PROD-12", CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("pear", 0.99, "TEST-PROD-14", CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("orange", 1.09, "TEST-PROD-16", CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("banana", 0.49, "TEST-PROD-17", CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("plum", 0.89, "TEST-PROD-18", CatalogProduct.Department.GROCERY));

        Iterable<CatalogProduct> found = catalog.findByProductNumBetween("TEST-PROD-13", "TEST-PROD-17", Order.by(Sort.asc("name")));
        Iterator<CatalogProduct> it = found.iterator();
        assertEquals(true, it.hasNext());
        assertEquals("banana", it.next().getName());
        assertEquals(true, it.hasNext());
        assertEquals("orange", it.next().getName());
        assertEquals(true, it.hasNext());
        assertEquals("pear", it.next().getName());
        assertEquals(false, it.hasNext());

        assertEquals(5L, catalog.deleteByProductNumLike("TEST-PROD-%"));
    }

    @Test
    public void testInsertEntityThatAlreadyExists() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        CatalogProduct prod1 = catalog.add(CatalogProduct.of("watermelon", 6.29, "TEST-PROD-94", CatalogProduct.Department.GROCERY));

        try {
            catalog.add(CatalogProduct.of("pineapple", 1.99, "TEST-PROD-94", GROCERY));
            fail("Should not be able to insert an entity that has same Id as another entity.");
        } catch (EntityExistsException x) {
            // expected
        }

        Optional<CatalogProduct> result;
        result = catalog.get("TEST-PROD-94");
        assertEquals(true, result.isPresent());

        catalog.remove(prod1);

        result = catalog.get("TEST-PROD-94");
        assertEquals(false, result.isPresent());
    }

    @Test
    public void testSaveChoosesInsertOrUpdate() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        CatalogProduct product = CatalogProduct.of("save insert", 1.99, "TEST-PROD-SAVE", GROCERY);
        catalog.customSave(product);

        Optional<CatalogProduct> found = catalog.get("TEST-PROD-SAVE");
        assertEquals(true, found.isPresent());
        assertEquals("save insert", found.orElseThrow().getName());

        product = found.orElseThrow();
        product.setName("save update");
        product.setPrice(2.49);
        catalog.customSave(product);

        found = catalog.get("TEST-PROD-SAVE");
        assertEquals(true, found.isPresent());
        assertEquals("save update", found.orElseThrow().getName());
        assertEquals(2.49, found.orElseThrow().getPrice(), 0.001);
        assertEquals(1L, catalog.deleteByProductNumLike("TEST-PROD-%"));
    }

    @Test
    public void testSaveAllChoosesInsertOrUpdate() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        CatalogProduct existing = CatalogProduct.of("save all existing", 4.99, "TEST-PROD-SAVE-ALL-1", GROCERY);
        catalog.customSave(existing);
        existing = catalog.get("TEST-PROD-SAVE-ALL-1").orElseThrow();
        existing.setName("save all existing updated");
        existing.setPrice(5.49);

        CatalogProduct[] saved = catalog.customSaveMultiple(
            CatalogProduct.of("save all insert", 1.99, "TEST-PROD-SAVE-ALL-2", GROCERY),
            existing,
            CatalogProduct.of("save all insert 2", 2.99, "TEST-PROD-SAVE-ALL-3", GROCERY)
        );

        assertEquals(3, saved.length);
        assertEquals("save all insert", saved[0].getName());
        assertEquals("save all existing updated", saved[1].getName());
        assertEquals("save all insert 2", saved[2].getName());
        Optional<CatalogProduct> found = catalog.get("TEST-PROD-SAVE-ALL-1");
        assertEquals(true, found.isPresent());
        assertEquals("save all existing updated", found.orElseThrow().getName());
        assertEquals(5.49, found.orElseThrow().getPrice(), 0.001);
        assertEquals(3L, catalog.deleteByProductNumLike("TEST-PROD-%"));
    }

    @Test
    public void testLike() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        catalog.customSave(CatalogProduct.of("celery", 1.57, "TEST-PROD-31", GROCERY));
        catalog.customSave(CatalogProduct.of("mushrooms", 1.89, "TEST-PROD-32", GROCERY));
        catalog.customSave(CatalogProduct.of("carrots", 1.39, "TEST-PROD-33", GROCERY));

        List<CatalogProduct> found = catalog.findByNameLike("%r_o%");
        assertEquals(List.of("carrots", "mushrooms"),
                     found.stream().map(CatalogProduct::getName).sorted().collect(Collectors.toList()));

        assertEquals(3L, catalog.deleteByProductNumLike("TEST-PROD-%"));
    }

    @Test
    public void testNotRunOnNOSQL() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        List<CatalogProduct> products = new ArrayList<>();
        products.add(CatalogProduct.of("pen", 2.50, "TEST-PROD-01"));
        products.add(CatalogProduct.of("pencil", 1.25, "TEST-PROD-02"));
        products.add(CatalogProduct.of("marker", 3.00, "TEST-PROD-03"));
        products.add(CatalogProduct.of("calculator", 15.00, "TEST-PROD-04"));
        products.add(CatalogProduct.of("ruler", 2.00, "TEST-PROD-05"));

        products.forEach(product -> catalog.customSave(product));

        long countExpensive = catalog.countByPriceGreaterThanEqual(2.99);
        assertEquals(2L, countExpensive, "Expected two products to be more than 3.00");

        assertEquals(5L, catalog.deleteByProductNumLike("TEST-PROD-%"));
    }

    @Disabled // Pending feature
    @Test
    public void testMultipleInsertUpdateDelete() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        CatalogProduct[] added = catalog.addMultiple(CatalogProduct.of("blueberries", 2.49, "TEST-PROD-95", CatalogProduct.Department.GROCERY),
            CatalogProduct.of("strawberries", 2.29, "TEST-PROD-96", CatalogProduct.Department.GROCERY),
            CatalogProduct.of("raspberries", 2.39, "TEST-PROD-97", CatalogProduct.Department.GROCERY));

        assertEquals(3, added.length);

        // The position of resulting entities must match the parameter
        assertEquals("blueberries", added[0].getName());
        assertEquals("TEST-PROD-95", added[0].getProductNum());
        assertEquals(2.49, added[0].getPrice(), 0.001);
        assertEquals(Set.of(CatalogProduct.Department.GROCERY), added[0].getDepartments());
        CatalogProduct blueberries = added[0];

        assertEquals("strawberries", added[1].getName());
        assertEquals("TEST-PROD-96", added[1].getProductNum());
        assertEquals(2.29, added[1].getPrice(), 0.001);
        assertEquals(Set.of(CatalogProduct.Department.GROCERY), added[1].getDepartments());
        CatalogProduct strawberries = added[1];

        assertEquals("raspberries", added[2].getName());
        assertEquals("TEST-PROD-97", added[2].getProductNum());
        assertEquals(2.39, added[2].getPrice(), 0.001);
        assertEquals(Set.of(CatalogProduct.Department.GROCERY), added[2].getDepartments());
        CatalogProduct raspberries = added[2];

        strawberries.setPrice(1.99);
        raspberries.setPrice(2.34);
        CatalogProduct[] modified = catalog.modifyMultiple(raspberries, strawberries);
        assertEquals(2, modified.length);

        assertEquals("raspberries", modified[0].getName());
        assertEquals("TEST-PROD-97", modified[0].getProductNum());
        assertEquals(2.34, modified[0].getPrice(), 0.001);
        assertEquals(Set.of(CatalogProduct.Department.GROCERY), modified[0].getDepartments());
        raspberries = modified[0];

        assertEquals("strawberries", modified[1].getName());
        assertEquals("TEST-PROD-96", modified[1].getProductNum());
        assertEquals(1.99, modified[1].getPrice(), 0.001);
        assertEquals(Set.of(CatalogProduct.Department.GROCERY), modified[1].getDepartments());
        strawberries = modified[1];

        // Attempt to remove entities that do not exist in the database
        try {
            catalog.removeMultiple(CatalogProduct.of("blackberries", 2.59, "TEST-PROD-98", CatalogProduct.Department.GROCERY),
                CatalogProduct.of("gooseberries", 2.79, "TEST-PROD-99", CatalogProduct.Department.GROCERY));
            fail("OptimisticLockingFailureException must be raised because the entities are not found for deletion.");
        } catch (OptimisticLockingFailureException x) {
            // expected
        }

        // Remove only the entities that actually exist in the database
        catalog.removeMultiple(strawberries, blueberries, raspberries);

        Iterable<CatalogProduct> remaining = catalog.findByProductNumBetween("TEST-PROD-95", "TEST-PROD-99", Order.by());
        assertEquals(false, remaining.iterator().hasNext());
    }

    @Test
    public void testNull() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        catalog.customSave(CatalogProduct.of("spinach", 2.28, "TEST-PROD-51", CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("broccoli", 2.49, "TEST-PROD-52", CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("rhubarb", null, "TEST-PROD-53", CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("potato", 0.79, "TEST-PROD-54", CatalogProduct.Department.GROCERY));

        Collection<CatalogProduct> found = catalog.findByPriceNull();

        assertEquals(1, found.size());
        assertEquals("rhubarb", found.iterator().next().getName());

        assertEquals(List.of("spinach", "potato"),
                     catalog.findByPriceNotNullAndPriceLessThanEqual(2.30)
                                     .map(CatalogProduct::getName)
                                     .collect(Collectors.toList()));

        assertEquals(4L, catalog.deleteByProductNumLike("TEST-PROD-%"));
    }

    @Test
    public void testSort() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        catalog.customSave(CatalogProduct.of("sweater", 23.88, "TEST-PROD-71", CatalogProduct.Department.CLOTHING));
        catalog.customSave(CatalogProduct.of("toothpaste", 2.39, "TEST-PROD-72", CatalogProduct.Department.PHARMACY, CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("chisel", 5.99, "TEST-PROD-73", CatalogProduct.Department.TOOLS));
        catalog.customSave(CatalogProduct.of("computer", 1299.50, "TEST-PROD-74", CatalogProduct.Department.ELECTRONICS, CatalogProduct.Department.OFFICE));
        catalog.customSave(CatalogProduct.of("sunblock", 5.98, "TEST-PROD-75", CatalogProduct.Department.PHARMACY, CatalogProduct.Department.SPORTING_GOODS, CatalogProduct.Department.GARDEN));
        catalog.customSave(CatalogProduct.of("basketball", 14.88, "TEST-PROD-76", CatalogProduct.Department.SPORTING_GOODS));
        catalog.customSave(CatalogProduct.of("baseball cap", 12.99, "TEST-PROD-77", CatalogProduct.Department.SPORTING_GOODS, CatalogProduct.Department.CLOTHING));

        List<CatalogProduct> found = catalog.findByNameLengthAndPriceBelowNameAsc(10, 100.0);

        assertEquals(List.of("basketball", "toothpaste"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        found = catalog.findByNameLengthAndPriceBelowNameDesc(10, 100.0);

        assertEquals(List.of("toothpaste", "basketball"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        found = catalog.allSortedByNameAsc();

        assertEquals(List.of("baseball cap", "basketball", "chisel", "computer", "sunblock", "sweater", "toothpaste"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        found = catalog.allSortedByNameDesc();

        assertEquals(List.of("toothpaste", "sweater", "sunblock", "computer", "chisel", "basketball", "baseball cap"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

    }

    @Test
    public void testSortIgnoreCase() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        catalog.customSave(CatalogProduct.of("Sweater", 23.88, "TEST-PROD-71", CatalogProduct.Department.CLOTHING));
        catalog.customSave(CatalogProduct.of("toothpaste", 2.39, "TEST-PROD-72", CatalogProduct.Department.PHARMACY, CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("Chisel", 5.99, "TEST-PROD-73", CatalogProduct.Department.TOOLS));
        catalog.customSave(CatalogProduct.of("computer", 1299.50, "TEST-PROD-74", CatalogProduct.Department.ELECTRONICS, CatalogProduct.Department.OFFICE));
        catalog.customSave(CatalogProduct.of("Sunblock", 5.98, "TEST-PROD-75", CatalogProduct.Department.PHARMACY, CatalogProduct.Department.SPORTING_GOODS, CatalogProduct.Department.GARDEN));
        catalog.customSave(CatalogProduct.of("basketball", 14.88, "TEST-PROD-76", CatalogProduct.Department.SPORTING_GOODS));
        catalog.customSave(CatalogProduct.of("Baseball cap", 12.99, "TEST-PROD-77", CatalogProduct.Department.SPORTING_GOODS, CatalogProduct.Department.CLOTHING));

        List<CatalogProduct> found;

        found = catalog.allSortedByNameAsc();

        assertEquals(List.of("Baseball cap", "Chisel", "Sunblock", "Sweater", "basketball", "computer", "toothpaste"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        found = catalog.allSortedByNameDesc();

        assertEquals(List.of("toothpaste", "computer", "basketball", "Sweater", "Sunblock", "Chisel", "Baseball cap"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        found = catalog.allSortedByNameAscIgnoreCase();

        assertEquals(List.of("Baseball cap", "basketball", "Chisel", "computer", "Sunblock", "Sweater", "toothpaste"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        found = catalog.allSortedByNameDescIgnoreCase();

        assertEquals(List.of("toothpaste", "Sweater", "Sunblock", "computer", "Chisel", "basketball", "Baseball cap"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        found = catalog.findAll();

        assertEquals(List.of("toothpaste", "Sweater", "Sunblock", "computer", "Chisel", "basketball", "Baseball cap"),
            found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));
    }

    @Test
    public void testQueryWithNamedParameters() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        catalog.customSave(CatalogProduct.of("tape measure", 7.29, "TEST-PROD-61", CatalogProduct.Department.TOOLS));
        catalog.customSave(CatalogProduct.of("pry bar", 4.39, "TEST-PROD-62", CatalogProduct.Department.TOOLS));
        catalog.customSave(CatalogProduct.of("hammer", 8.59, "TEST-PROD-63", CatalogProduct.Department.TOOLS));
        catalog.customSave(CatalogProduct.of("adjustable wrench", 4.99, "TEST-PROD-64", CatalogProduct.Department.TOOLS));
        catalog.customSave(CatalogProduct.of("framing square", 9.88, "TEST-PROD-65", CatalogProduct.Department.TOOLS));
        catalog.customSave(CatalogProduct.of("rasp", 6.79, "TEST-PROD-66", CatalogProduct.Department.TOOLS));

        Stream<CatalogProduct> found = catalog.withTaxBetween(0.4, 0.6, 0.08125);

        assertEquals(List.of("adjustable wrench", "rasp", "tape measure"),
                     found.map(CatalogProduct::getName).collect(Collectors.toList()));

        assertEquals(6L, catalog.deleteByProductNumLike("TEST-PROD-%"));
    }

    @Test
    public void testQueryWithPositionalParameters() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        catalog.customSave(CatalogProduct.of("sweater", 23.88, "TEST-PROD-71", CatalogProduct.Department.CLOTHING));
        catalog.customSave(CatalogProduct.of("toothpaste", 2.39, "TEST-PROD-72", CatalogProduct.Department.PHARMACY, CatalogProduct.Department.GROCERY));
        catalog.customSave(CatalogProduct.of("chisel", 5.99, "TEST-PROD-73", CatalogProduct.Department.TOOLS));
        catalog.customSave(CatalogProduct.of("computer", 1299.50, "TEST-PROD-74", CatalogProduct.Department.ELECTRONICS, CatalogProduct.Department.OFFICE));
        catalog.customSave(CatalogProduct.of("sunblock", 5.98, "TEST-PROD-75", CatalogProduct.Department.PHARMACY, CatalogProduct.Department.SPORTING_GOODS, CatalogProduct.Department.GARDEN));
        catalog.customSave(CatalogProduct.of("basketball", 14.88, "TEST-PROD-76", CatalogProduct.Department.SPORTING_GOODS));
        catalog.customSave(CatalogProduct.of("baseball cap", 12.99, "TEST-PROD-77", CatalogProduct.Department.SPORTING_GOODS, CatalogProduct.Department.CLOTHING));

        List<CatalogProduct> found = catalog.findByNameLengthAndPriceBelow(10, 100.0);

        assertEquals(List.of("basketball", "toothpaste"),
                     found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        found = catalog.findByNameLengthAndPriceBelow(8, 1000.0);

        assertEquals(List.of("sunblock"),
                     found.stream().map(CatalogProduct::getName).collect(Collectors.toList()));

        assertEquals(7L, catalog.deleteByProductNumLike("TEST-PROD-%"));

    }

    @Disabled // Pending feature
    @Test
    public void testVersionedInsertUpdateDelete() {
        catalog.deleteByProductNumLike("TEST-PROD-%");

        CatalogProduct prod1 = catalog.add(CatalogProduct.of("zucchini", 1.49, "TEST-PROD-91", CatalogProduct.Department.GROCERY));
        CatalogProduct prod2 = catalog.add(CatalogProduct.of("cucumber", 1.29, "TEST-PROD-92", CatalogProduct.Department.GROCERY));

        long prod1InitialVersion = prod1.getVersionNum();
        long prod2InitialVersion = prod2.getVersionNum();

        prod1.setPrice(1.59);
        prod1 = catalog.modify(prod1);

        prod2.setPrice(1.39);
        prod2 = catalog.modify(prod2);

        // Expect version number to change when modified
        assertNotEquals(prod1InitialVersion, prod1.getVersionNum());
        assertNotEquals(prod2InitialVersion, prod2.getVersionNum());

        long prod1SecondVersion = prod1.getVersionNum();

        prod1.setPrice(1.54);
        prod1 = catalog.modify(prod1);

        assertNotEquals(prod1SecondVersion, prod1.getVersionNum());
        assertNotEquals(prod1InitialVersion, prod1.getVersionNum());

        // Update must not be made when the version does not match:
        prod2.setVersionNum(prod2InitialVersion);
        prod2.setPrice(1.34);
        try {
            catalog.modify(prod2);
            fail("Must raise OptimisticLockingFailureException for entity instance with old version.");
        } catch (OptimisticLockingFailureException x) {
            // expected
        }

        catalog.remove(prod1);

        Optional<CatalogProduct> found = catalog.get("TEST-PROD-91");
        assertEquals(false, found.isPresent());

        try {
            catalog.remove(prod1); // already removed
            fail("Must raise OptimisticLockingFailureException for entity that was already removed from the database.");
        } catch (OptimisticLockingFailureException x) {
            // expected
        }

        prod2.setVersionNum(prod2InitialVersion);
        try {
            catalog.remove(prod2); // still at old version
            fail("Must raise OptimisticLockingFailureException for entity with non-matching version.");
        } catch (OptimisticLockingFailureException x) {
            // expected
        }

        assertEquals(1L, catalog.deleteByProductNumLike("TEST-PROD-%"));
    }
}
