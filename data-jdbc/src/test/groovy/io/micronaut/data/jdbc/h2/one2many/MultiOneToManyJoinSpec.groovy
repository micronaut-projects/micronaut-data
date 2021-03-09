package io.micronaut.data.jdbc.h2.one2many

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
@H2DBProperties
class MultiOneToManyJoinSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject CategoryRepository categoryRepository = applicationContext.getBean(CategoryRepository)

    void 'test handle null values in joins'() {
        expect:"no results are returned"
        categoryRepository.findAll().isEmpty()
    }

}

@JdbcRepository(dialect = Dialect.H2)
interface CategoryRepository extends CrudRepository<Category, Long> {

    @Join(value = "productList", alias =  "p_", type =  Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption", alias =  "op_", type =  Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption.option", alias =  "opno_", type =  Join.Type.LEFT_FETCH)
    @Override
    List<Category> findAll()
}
@MappedEntity("mo2m_category")
class Category {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "categoryId")
    List<Product> productList

    Category(Long id, String name, List<Product> productList) {
        this.id = id
        this.name = name
        this.productList = productList
    }
}

@MappedEntity("mo2m_product")
class Product {
    @Id
    @GeneratedValue
    Long id
    String name
    Long categoryId
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "productId")
    List<ProductOption> productOption

    Product(Long id, String name, Long categoryId, List<ProductOption> productOption) {
        this.id = id
        this.name = name
        this.categoryId = categoryId
        this.productOption = productOption
    }
}

@MappedEntity("mo2m_product_option")
class ProductOption {
    @Id
    @GeneratedValue
    Long id
    String name
    Long productId
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "productOptionId")
    List<Option> option

    ProductOption(Long id, String name, Long productId, List<Option> option) {
        this.id = id
        this.name = name
        this.productId = productId
        this.option = option
    }
}

@MappedEntity("mo2m_option")
class Option {
    @Id
    @GeneratedValue
    Long id
    String name
    Long productOptionId

    Option(Long id, String name, Long productOptionId) {
        this.id = id
        this.name = name
        this.productOptionId = productOptionId
    }
}