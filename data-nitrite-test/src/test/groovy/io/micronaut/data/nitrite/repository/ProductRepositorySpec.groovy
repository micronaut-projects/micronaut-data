package io.micronaut.data.nitrite.repository

import io.micronaut.data.model.Pageable
import io.micronaut.data.nitrite.model.Document
import io.micronaut.data.nitrite.model.Product
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

/**
 * Integration tests for Nitrite-backed repositories using {@link ProductRepository}.
 *
 * <p>This spec intentionally mixes “normal” derived-query coverage with a few targeted regression
 * tests that protect Nitrite-specific contracts:
 *
 * <ul>
 *   <li><b>Numeric equality stability</b>: depending on the mapping path, numeric values may be
 *       stored as different Java numeric types (for example {@code Long} vs {@code BigDecimal}).
 *       ID lookups must remain stable (see the “Gap 4” test).</li>
 *   <li><b>UUID round-tripping</b>: UUID fields are stored as strings by Jackson; equality must work
 *       after reload (see the “Gap 5” tests).</li>
 *   <li><b>BigDecimal ordering</b>: comparisons must be numeric, not lexicographic (see “Gap 8”).</li>
 * </ul>
 */
@MicronautTest(transactional = false)
class ProductRepositorySpec extends Specification {

    @Inject
    ProductRepository productRepository

    @Inject
    DocumentRepository documentRepository

    def setup() {
        productRepository.deleteAll()
    }

    // ========== Section 1: Long ID Type Tests ==========

    void "test save with Long generated ID"() {
        given:
        def product = new Product("Widget", new BigDecimal("19.99"), 100)

        when:
        def saved = productRepository.save(product)

        then:
        saved.id != null
        saved.id instanceof Long
        saved.name == "Widget"
        saved.price == new BigDecimal("19.99")
    }

    void "test find by Long ID"() {
        given:
        def saved = productRepository.save(new Product("Gadget", new BigDecimal("29.99"), 50))

        when:
        def found = productRepository.findById(saved.id)

        then:
        found.isPresent()
        found.get().name == "Gadget"
    }

    // ========== Section 2: BigDecimal Field Tests ==========

    void "test find by price greater than"() {
        given:
        productRepository.save(new Product("Cheap", new BigDecimal("9.99"), 100))
        productRepository.save(new Product("Medium", new BigDecimal("49.99"), 50))
        productRepository.save(new Product("Expensive", new BigDecimal("99.99"), 10))

        when:
        def results = productRepository.findByPriceGreaterThan(new BigDecimal("25.00"))

        then:
        results.size() == 2
        results.every { it.price > new BigDecimal("25.00") }
    }

    void "test find by price less than"() {
        given:
        productRepository.save(new Product("Cheap", new BigDecimal("9.99"), 100))
        productRepository.save(new Product("Medium", new BigDecimal("49.99"), 50))
        productRepository.save(new Product("Expensive", new BigDecimal("99.99"), 10))

        when:
        def results = productRepository.findByPriceLessThan(new BigDecimal("50.00"))

        then:
        results.size() == 2
        results.every { it.price < new BigDecimal("50.00") }
    }

    void "test find by price between"() {
        given:
        productRepository.save(new Product("Cheap", new BigDecimal("9.99"), 100))
        productRepository.save(new Product("Medium", new BigDecimal("49.99"), 50))
        productRepository.save(new Product("Expensive", new BigDecimal("99.99"), 10))

        when:
        def results = productRepository.findByPriceBetween(new BigDecimal("20.00"), new BigDecimal("60.00"))

        then:
        results.size() == 1
        results[0].name == "Medium"
    }

    void "test find by price not equal"() {
        given:
        productRepository.save(new Product("A", new BigDecimal("10.00"), 100))
        productRepository.save(new Product("B", new BigDecimal("20.00"), 50))
        productRepository.save(new Product("C", new BigDecimal("30.00"), 25))

        when:
        def results = productRepository.findByPriceNot(new BigDecimal("20.00"))

        then:
        results.size() == 2
        results*.name.containsAll(["A", "C"])
    }

    // ========== Section 3: Integer Field Tests ==========

    void "test find by quantity greater than"() {
        given:
        productRepository.save(new Product("Low Stock", new BigDecimal("10.00"), 5))
        productRepository.save(new Product("Medium Stock", new BigDecimal("20.00"), 50))
        productRepository.save(new Product("High Stock", new BigDecimal("30.00"), 100))

        when:
        def results = productRepository.findByQuantityGreaterThan(25)

        then:
        results.size() == 2
        results.every { it.quantity > 25 }
    }

    void "test find by quantity less than or equals"() {
        given:
        productRepository.save(new Product("Low Stock", new BigDecimal("10.00"), 5))
        productRepository.save(new Product("Medium Stock", new BigDecimal("20.00"), 50))
        productRepository.save(new Product("High Stock", new BigDecimal("30.00"), 100))

        when:
        def results = productRepository.findByQuantityLessThanEquals(50)

        then:
        results.size() == 2
        results.every { it.quantity <= 50 }
    }

    // ========== Section 4: Boolean Field Tests ==========

    void "test boolean available field via filtering"() {
        given:
        def p1 = new Product("Available1", new BigDecimal("10.00"), 100)
        p1.setAvailable(true)
        def p2 = new Product("Available2", new BigDecimal("20.00"), 50)
        p2.setAvailable(true)
        def p3 = new Product("Unavailable", new BigDecimal("30.00"), 25)
        p3.setAvailable(false)
        
        productRepository.save(p1)
        productRepository.save(p2)
        productRepository.save(p3)

        when:
        def all = productRepository.findAll(Pageable.from(0, 10))
        def availableResults = all.findAll { it.available == true }
        def unavailableResults = all.findAll { it.available == false }

        then:
        availableResults.size() == 2
        unavailableResults.size() == 1
    }

    // ========== Section 5: UUID Field Tests ==========

    void "test UUID field is stored"() {
        given:
        def product = new Product("UUID Test", new BigDecimal("15.00"), 10)

        when:
        def saved = productRepository.save(product)

        then:
        saved.sku != null
        saved.sku instanceof UUID
    }

    // ========== Section 6: Combined Queries ==========

    void "test combined price and availability query"() {
        given:
        def p1 = new Product("Cheap Available", new BigDecimal("10.00"), 100)
        p1.setAvailable(true)
        def p2 = new Product("Expensive Available", new BigDecimal("100.00"), 50)
        p2.setAvailable(true)
        def p3 = new Product("Cheap Unavailable", new BigDecimal("10.00"), 25)
        p3.setAvailable(false)
        
        productRepository.save(p1)
        productRepository.save(p2)
        productRepository.save(p3)

        when:
        def all = productRepository.findAll(Pageable.from(0, 10))
        def cheapAvailable = all.findAll { it.price < new BigDecimal("50.00") && it.available == true }

        then:
        cheapAvailable.size() == 1
        cheapAvailable[0].name == "Cheap Available"
    }

    void "test pagination with sort"() {
        given:
        productRepository.save(new Product("C", new BigDecimal("30.00"), 100))
        productRepository.save(new Product("A", new BigDecimal("10.00"), 50))
        productRepository.save(new Product("B", new BigDecimal("20.00"), 75))

        when:
        def page = productRepository.findAll(Pageable.from(0, 2, io.micronaut.data.model.Sort.of(io.micronaut.data.model.Sort.Order.asc("name"))))

        then:
        page.content.size() == 2
        page.totalSize == 3
        page.content[0].name == "A"
        page.content[1].name == "B"
    }

    // ========== Gap Coverage Tests ==========

    /**
     * Gap 4: Long ID in filter (findOne path).
     *
     * <p>Regression guard for numeric equality coercion. If the stored {@code id} is represented as
     * a different numeric type than the query parameter (e.g. {@code BigDecimal} vs {@code Long}),
     * a strict equality filter can return {@code Optional.empty}.
     */
    void "test findById with Long ID"() {
        given:
        def product = new Product("TestProduct", new BigDecimal("15.99"), 10)
        def saved = productRepository.save(product)
        def longId = saved.id

        when:
        def found = productRepository.findById(longId)

        then:
        found.present
        found.get().id == longId
        found.get().name == "TestProduct"
    }

    /**
     * Gap 5: UUID sku field persistence.
     *
     * <p>Regression guard for UUID mapping. The Nitrite Jackson mapper serializes UUIDs as strings;
     * the runtime must preserve value equality across save + reload.
     */
    void "test UUID sku field persistence - Gap 5"() {
        given:
        def product = new Product("TestProduct", new BigDecimal("19.99"), 10)
        def originalSku = product.sku

        when: "Save product"
        def saved = productRepository.save(product)

        and: "Reload from database"
        def found = productRepository.findById(saved.id).get()

        then:
        found.sku == originalSku  // UUID round-trips correctly
        found.sku instanceof UUID
    }

    void "test UUID sku field unique per product - Gap 5"() {
        given:
        def product1 = new Product("Product1", new BigDecimal("19.99"), 10)
        def product2 = new Product("Product2", new BigDecimal("29.99"), 20)
        def sku1 = product1.sku
        def sku2 = product2.sku

        expect: "Each product has unique UUID"
        sku1 != sku2
        sku1 instanceof UUID
        sku2 instanceof UUID

        when: "Save products separately and reload"
        def saved1 = productRepository.save(product1)
        def found1 = productRepository.findById(saved1.id).get()
        productRepository.deleteAll()  // Clear to avoid ID collision in test
        
        def saved2 = productRepository.save(product2)
        def found2 = productRepository.findById(saved2.id).get()

        then: "UUIDs preserved"
        found1.sku == sku1
        found2.sku == sku2
    }

    void "test UUID sku field query via in-memory filter - Gap 5"() {
        given:
        def product = new Product("Product1", new BigDecimal("19.99"), 10)
        productRepository.save(product)
        def sku = product.sku

        when:
        def all = productRepository.findAll()
        def found = all.find { it.sku == sku }

        then:
        found != null
        found.name == "Product1"
        found.sku == sku
    }

    /**
     * Gap 8: BigDecimal comparison ordering (lexicographic vs numeric).
     *
     * <p>Regression guard that comparisons are numeric. A buggy implementation that coerces values
     * to strings can produce lexicographic ordering (e.g. "9.99" > "10.00").
     */
    void "test BigDecimal comparison with edge cases"() {
        given:
        // These values would fail under lexicographic ordering: "9.99" > "10.00"
        productRepository.save(new Product("P1", new BigDecimal("9.99"), 10))
        productRepository.save(new Product("P2", new BigDecimal("10.00"), 20))
        productRepository.save(new Product("P3", new BigDecimal("10.01"), 30))
        productRepository.save(new Product("P4", new BigDecimal("99.99"), 40))

        when:
        def results = productRepository.findByPriceGreaterThan(new BigDecimal("10.00"))

        then:
        results.size() == 2
        results*.name.containsAll(["P3", "P4"])
        // "P1" (9.99) should NOT be included - proves numeric, not lexicographic ordering
    }

    void "test BigDecimal between boundaries"() {
        given:
        productRepository.save(new Product("P1", new BigDecimal("9.99"), 10))
        productRepository.save(new Product("P2", new BigDecimal("10.00"), 20))
        productRepository.save(new Product("P3", new BigDecimal("15.00"), 30))
        productRepository.save(new Product("P4", new BigDecimal("20.00"), 40))
        productRepository.save(new Product("P5", new BigDecimal("20.01"), 50))

        when:
        def results = productRepository.findByPriceBetween(new BigDecimal("10.00"), new BigDecimal("20.00"))

        then:
        results.size() == 3
        results*.name.containsAll(["P2", "P3", "P4"])
        // Boundaries should be inclusive
    }

    // Gap 3: Cross-collection isolation test
    void "test cross-collection isolation"() {
        given:
        // Save data in both collections
        def doc = new Document("TestDoc", ["test"], true)
        documentRepository.save(doc)

        def product = new Product("TestProduct", new BigDecimal("19.99"), 10)
        productRepository.save(product)

        when:
        def allDocs = documentRepository.findAll()
        def allProducts = productRepository.findAll()

        then:
        allDocs.size() == 1
        allProducts.size() == 1
        allDocs[0].title == "TestDoc"
        allProducts[0].name == "TestProduct"

        and: "Deleting from one collection doesn't affect the other"
        documentRepository.deleteAll()
        productRepository.count() == 1
    }
}
