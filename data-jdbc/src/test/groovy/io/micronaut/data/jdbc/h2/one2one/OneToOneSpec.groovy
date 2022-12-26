package io.micronaut.data.jdbc.h2.one2one

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.naming.NamingStrategies
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.time.LocalDateTime

@MicronautTest
@H2DBProperties
class OneToOneSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    CustomerRepository customerRepository = applicationContext.getBean(CustomerRepository)

    @Shared
    @Inject
    Connection connection

    @Override
    SchemaGenerate schemaGenerate() {
        return SchemaGenerate.NONE
    }

    void 'test'() {
        given:
            try (def s = connection.createStatement()) {
                s.execute('''
DROP TABLE IF EXISTS `TestXyzCategory`;
DROP TABLE IF EXISTS `TestXyzCustomer`;
DROP TABLE IF EXISTS `TestXyzCustomerDetails`;

CREATE OR REPLACE TABLE `TestXyzCategory` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `active` boolean DEFAULT NULL,
  `createdAt` datetime(6) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `priority` bigint DEFAULT NULL
);

CREATE OR REPLACE TABLE `TestXyzCustomer` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `createdAt` datetime(6) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `showCustomer` boolean DEFAULT NULL
);

CREATE OR REPLACE TABLE `TestXyzCustomerDetails` (
  `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  `createdAt` datetime(6) NOT NULL,
  `detail` varchar(255) DEFAULT NULL,
  `label` varchar(255) DEFAULT NULL,
  `updatedAt` datetime(6) NOT NULL,
  `categoryId` bigint DEFAULT NULL,
  `customerId` bigint DEFAULT NULL
);

INSERT INTO TestXyzCategory
(active, createdAt, name, priority)
VALUES(true, '2020-03-08 21:40:34', '24h', true);

INSERT INTO TestXyzCustomer
(createdAt, name, showCustomer)
VALUES('2020-03-08 21:40:34', 'Alfa', true);

INSERT INTO TestXyzCustomerDetails
(createdAt, detail, label, updatedAt, categoryId, customerId)
VALUES('2020-03-08 21:40:34', 'detail', 'label', '2020-03-08 21:40:34', 1, 1);


''')
            }
        expect:
            customerRepository.findByCustomer(new Customer(id: 1)).size() == 1
            customerRepository.findByCustomerAndCategoryActive(new Customer(id: 1), true).size() == 1
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface CustomerRepository extends CrudRepository<CustomerDetails, Long> {

    @Join("customer")
    @Join("category")
    List<CustomerDetails> findByCustomerAndCategoryActive(Customer customer, Boolean active)

    @Join("customer")
    List<CustomerDetails> findByCustomer(Customer customer)
}

@MappedEntity(value = "TestXyzCustomerDetails", namingStrategy = NamingStrategies.Raw.class)
class CustomerDetails {

    @Id
    @GeneratedValue
    Long id
    @Relation(value = Relation.Kind.ONE_TO_ONE)
    @MappedProperty("customerId")
    Customer customer

    @Relation(value = Relation.Kind.ONE_TO_ONE)
    @MappedProperty("categoryId")
    Category category

    String label
    String detail

    @DateCreated
    LocalDateTime createdAt

    @DateUpdated
    LocalDateTime updatedAt
}


@MappedEntity(value = "TestXyzCategory", namingStrategy = NamingStrategies.Raw.class)
class Category {

    @Id
    @GeneratedValue
    Long id
    String name

    Boolean active

    @DateCreated
    LocalDateTime createdAt

    Long priority
}

@MappedEntity(value = "TestXyzCustomer", namingStrategy = NamingStrategies.Raw.class)
class Customer {

    @Id
    @GeneratedValue
    Long id
    String name

    @DateCreated
    LocalDateTime createdAt

    Boolean showCustomer

}
