package io.micronaut.data.jdbc.sqlite.one2one;

import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties(schemaGenerate = "NONE", packages = "io.micronaut.data.jdbc.sqlite.one2one")
class OneToOneTest {

    @Inject
    CustomerRepository customerRepository;

    @Inject
    Connection connection;

    @Test
    void test() throws SQLException {
        try (var s = connection.createStatement()) {
            for (String sql : List.of(
                "DROP TABLE IF EXISTS \"TestXyzCategory\"",
                "DROP TABLE IF EXISTS \"TestXyzCustomer\"",
                "DROP TABLE IF EXISTS \"TestXyzCustomerDetails\"",
                """
                CREATE TABLE "TestXyzCategory" (
                  "id" INTEGER PRIMARY KEY,
                  "active" BOOLEAN,
                  "createdAt" TEXT NOT NULL,
                  "name" VARCHAR(255),
                  "priority" BIGINT
                )
                """,
                """
                CREATE TABLE "TestXyzCustomer" (
                  "id" INTEGER PRIMARY KEY,
                  "createdAt" TEXT NOT NULL,
                  "name" VARCHAR(255),
                  "showCustomer" BOOLEAN
                )
                """,
                """
                CREATE TABLE "TestXyzCustomerDetails" (
                  "id" INTEGER PRIMARY KEY,
                  "createdAt" TEXT NOT NULL,
                  "detail" VARCHAR(255),
                  "label" VARCHAR(255),
                  "updatedAt" TEXT NOT NULL,
                  "categoryId" BIGINT,
                  "customerId" BIGINT
                )
                """,
                """
                INSERT INTO "TestXyzCategory" ("id", "active", "createdAt", "name", "priority")
                VALUES (1, 1, '2020-03-08 21:40:34', '24h', 1)
                """,
                """
                INSERT INTO "TestXyzCustomer" ("id", "createdAt", "name", "showCustomer")
                VALUES (1, '2020-03-08 21:40:34', 'Alfa', 1)
                """,
                """
                INSERT INTO "TestXyzCustomerDetails" ("id", "createdAt", "detail", "label", "updatedAt", "categoryId", "customerId")
                VALUES (1, '2020-03-08 21:40:34', 'detail', 'label', '2020-03-08 21:40:34', 1, 1)
                """
            )) {
                s.execute(sql);
            }
        }

        Customer customer = new Customer();
        customer.setId(1L);

        List<CustomerDetails> byCustomer = customerRepository.findByCustomer(customer);
        List<CustomerDetails> byCustomerAndCategoryActive = customerRepository.findByCustomerAndCategoryActive(customer, true);

        assertEquals(1, byCustomer.size());
        assertEquals(1, byCustomerAndCategoryActive.size());
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface CustomerRepository extends CrudRepository<CustomerDetails, Long> {

    @Join("customer")
    @Join("category")
    List<CustomerDetails> findByCustomerAndCategoryActive(Customer customer, Boolean active);

    @Join("customer")
    List<CustomerDetails> findByCustomer(Customer customer);
}

@MappedEntity(value = "TestXyzCustomerDetails", namingStrategy = NamingStrategies.Raw.class)
class CustomerDetails {
    @Id
    @GeneratedValue
    private Long id;
    @Relation(value = Relation.Kind.ONE_TO_ONE)
    @MappedProperty("customerId")
    private Customer customer;
    @Relation(value = Relation.Kind.ONE_TO_ONE)
    @MappedProperty("categoryId")
    private Category category;
    private String label;
    private String detail;
    @DateCreated
    private LocalDateTime createdAt;
    @DateUpdated
    private LocalDateTime updatedAt;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    Customer getCustomer() { return customer; }
    void setCustomer(Customer customer) { this.customer = customer; }
    Category getCategory() { return category; }
    void setCategory(Category category) { this.category = category; }
    String getLabel() { return label; }
    void setLabel(String label) { this.label = label; }
    String getDetail() { return detail; }
    void setDetail(String detail) { this.detail = detail; }
    LocalDateTime getCreatedAt() { return createdAt; }
    void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    LocalDateTime getUpdatedAt() { return updatedAt; }
    void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

@MappedEntity(value = "TestXyzCategory", namingStrategy = NamingStrategies.Raw.class)
class Category {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private Boolean active;
    @DateCreated
    private LocalDateTime createdAt;
    private Long priority;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
    Boolean getActive() { return active; }
    void setActive(Boolean active) { this.active = active; }
    LocalDateTime getCreatedAt() { return createdAt; }
    void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    Long getPriority() { return priority; }
    void setPriority(Long priority) { this.priority = priority; }
}

@MappedEntity(value = "TestXyzCustomer", namingStrategy = NamingStrategies.Raw.class)
class Customer {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @DateCreated
    private LocalDateTime createdAt;
    private Boolean showCustomer;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    String getName() { return name; }
    void setName(String name) { this.name = name; }
    LocalDateTime getCreatedAt() { return createdAt; }
    void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    Boolean getShowCustomer() { return showCustomer; }
    void setShowCustomer(Boolean showCustomer) { this.showCustomer = showCustomer; }
}
