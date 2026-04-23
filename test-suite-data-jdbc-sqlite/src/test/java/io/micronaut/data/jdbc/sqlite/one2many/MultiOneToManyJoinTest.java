package io.micronaut.data.jdbc.sqlite.one2many;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.JavaSQLiteDBProperties;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@JavaSQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.one2many")
class MultiOneToManyJoinTest {

    @Inject
    CategoryRepository categoryRepository;

    @Test
    void testHandleNullValuesInJoins() {
        assertTrue(categoryRepository.findAll().isEmpty());
    }

    @Test
    void testOneToManyHierarchy() {
        Category category = createCategory();

        categoryRepository.save(category);
        category = categoryRepository.findById(category.getId()).orElseThrow();

        assertCategory(category);

        categoryRepository.update(category);
        category = categoryRepository.findById(category.getId()).orElseThrow();

        assertCategory(category);

        category = categoryRepository.findAll().getFirst();
        assertCategory(category);
    }

    @Test
    void testJoinedCollectionShouldNotBeNull() {
        Category category = new Category();
        category.setName("Cats");
        category.setProductList(new ArrayList<>());

        categoryRepository.save(category);
        category = categoryRepository.findById(category.getId()).orElseThrow();

        assertNotNull(category.getProductList());
        assertTrue(category.getProductList().isEmpty());
    }

    @Test
    void testNotJoinedCollectionShouldBeNull() {
        Category category = new Category();
        category.setName("Cats");
        category.setProductList(new ArrayList<>());

        categoryRepository.save(category);
        category = categoryRepository.queryById(category.getId()).orElseThrow();

        assertNull(category.getProductList());
    }

    private Category createCategory() {
        Category category = new Category();
        category.setName("Cats");

        Product food = new Product();
        food.setName("Food");
        ProductOption pork = new ProductOption();
        pork.setName("Pork");
        setOptions(pork, "X", "Y", "Z");
        ProductOption beef = new ProductOption();
        beef.setName("Beef");
        setOptions(beef, "A", "B", "C");
        setProductOptions(food, pork, beef);

        Product toys = new Product();
        toys.setName("Toys");
        ProductOption ffff = new ProductOption();
        ffff.setName("Ffff");
        setOptions(ffff, "F1", "F2", "F3");
        ProductOption pfff = new ProductOption();
        pfff.setName("Pfff");
        setOptions(pfff, "P1", "P2", "P3");
        setProductOptions(toys, ffff, pfff);

        category.setProductList(new ArrayList<>(List.of(food, toys)));
        for (Product product : category.getProductList()) {
            product.setCategory(category);
        }
        return category;
    }

    private void setProductOptions(Product product, ProductOption... productOptions) {
        product.setProductOption(new ArrayList<>(List.of(productOptions)));
        for (ProductOption productOption : product.getProductOption()) {
            productOption.setProduct(product);
        }
    }

    private void setOptions(ProductOption productOption, String... names) {
        List<Option> options = new ArrayList<>();
        for (String name : names) {
            Option option = new Option();
            option.setName(name);
            option.setProductOption(productOption);
            options.add(option);
        }
        productOption.setOption(options);
    }

    private void assertCategory(Category category) {
        assertNotNull(category.getId());
        assertEquals("Cats", category.getName());
        assertEquals(2, category.getProductList().size());
        assertEquals("Food", category.getProductList().get(0).getName());
        assertEquals("Pork", category.getProductList().get(0).getProductOption().get(0).getName());
        assertEquals(3, category.getProductList().get(0).getProductOption().get(0).getOption().size());
        assertEquals("Beef", category.getProductList().get(0).getProductOption().get(1).getName());
        assertEquals(3, category.getProductList().get(0).getProductOption().get(1).getOption().size());
        assertEquals("Toys", category.getProductList().get(1).getName());
        assertEquals("Ffff", category.getProductList().get(1).getProductOption().get(0).getName());
        assertEquals(3, category.getProductList().get(1).getProductOption().get(0).getOption().size());
        assertEquals("Pfff", category.getProductList().get(1).getProductOption().get(1).getName());
        assertEquals(3, category.getProductList().get(1).getProductOption().get(1).getOption().size());
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface CategoryRepository extends CrudRepository<Category, Long> {

    @Join(value = "productList", alias = "p_", type = Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption", alias = "op_", type = Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption.option", alias = "opno_", type = Join.Type.LEFT_FETCH)
    @Override
    List<Category> findAll();

    @Join(value = "productList", alias = "p_", type = Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption", alias = "op_", type = Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption.option", alias = "opno_", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<Category> findById(Long id);

    Optional<Category> queryById(Long id);
}

@MappedEntity("mo2m_category")
class Category {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "category", cascade = Relation.Cascade.ALL)
    private List<Product> productList;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
    List<Product> getProductList() { return productList; }
    void setProductList(List<Product> productList) { this.productList = productList; }
}

@MappedEntity("mo2m_product")
class Product {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private Category category;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "product", cascade = Relation.Cascade.ALL)
    private List<ProductOption> productOption;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
    Category getCategory() { return category; }
    void setCategory(Category category) { this.category = category; }
    List<ProductOption> getProductOption() { return productOption; }
    void setProductOption(List<ProductOption> productOption) { this.productOption = productOption; }
}

@MappedEntity("mo2m_product_option")
class ProductOption {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private Product product;
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "productOption", cascade = Relation.Cascade.ALL)
    private List<Option> option;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
    Product getProduct() { return product; }
    void setProduct(Product product) { this.product = product; }
    List<Option> getOption() { return option; }
    void setOption(List<Option> option) { this.option = option; }
}

@MappedEntity("mo2m_option")
class Option {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private ProductOption productOption;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
    ProductOption getProductOption() { return productOption; }
    void setProductOption(ProductOption productOption) { this.productOption = productOption; }
}
