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

    void 'test one-to-many hierarchy'() {
        given:
            Category category = new Category(name: "Cats", productList: [
                    new Product(name: "Food", productOption: [
                            new ProductOption(name: "Pork", option: [new Option(name: "X"), new Option(name: "Y"), new Option(name: "Z")]),
                            new ProductOption(name: "Beef", option: [new Option(name: "A"), new Option(name: "B"), new Option(name: "C")])
                    ]),
                    new Product(name: "Toys", productOption:  [
                            new ProductOption(name: "Ffff", option: [new Option(name: "F1"), new Option(name: "F2"), new Option(name: "F3")]),
                            new ProductOption(name: "Pfff", option: [new Option(name: "P1"), new Option(name: "P2"), new Option(name: "P3")])
                    ])
            ])
        when:
            categoryRepository.save(category)
            category = categoryRepository.findById(category.id).get()
        then:
            category.id
            category.name == "Cats"
            category.productList.size() == 2
            category.productList[0].name == "Food"
            category.productList[0].productOption[0].name == "Pork"
            category.productList[0].productOption[0].option.size() == 3
            category.productList[0].productOption[1].name == "Beef"
            category.productList[0].productOption[1].option.size() == 3
            category.productList[1].name == "Toys"
            category.productList[1].productOption[0].name == "Ffff"
            category.productList[1].productOption[0].option.size() == 3
            category.productList[1].productOption[1].name == "Pfff"
            category.productList[1].productOption[1].option.size() == 3
        when:
            categoryRepository.update(category)
            category = categoryRepository.findById(category.id).get()
        then:
            category.id
            category.name == "Cats"
            category.productList.size() == 2
            category.productList[0].name == "Food"
            category.productList[0].productOption[0].name == "Pork"
            category.productList[0].productOption[0].option.size() == 3
            category.productList[0].productOption[1].name == "Beef"
            category.productList[0].productOption[1].option.size() == 3
            category.productList[1].name == "Toys"
            category.productList[1].productOption[0].name == "Ffff"
            category.productList[1].productOption[0].option.size() == 3
            category.productList[1].productOption[1].name == "Pfff"
            category.productList[1].productOption[1].option.size() == 3
        when:
            category = categoryRepository.findAll().first()
        then:
            category.id
            category.name == "Cats"
            category.productList.size() == 2
            category.productList[0].name == "Food"
            category.productList[0].productOption[0].name == "Pork"
            category.productList[0].productOption[0].option.size() == 3
            category.productList[0].productOption[1].name == "Beef"
            category.productList[0].productOption[1].option.size() == 3
            category.productList[1].name == "Toys"
            category.productList[1].productOption[0].name == "Ffff"
            category.productList[1].productOption[0].option.size() == 3
            category.productList[1].productOption[1].name == "Pfff"
            category.productList[1].productOption[1].option.size() == 3
    }

    void 'test joined collection should not be null'() {
        given:
            Category category = new Category(name: "Cats", productList: [])
        when:
            categoryRepository.save(category)
            category = categoryRepository.findById(category.id).get()
        then:
            category.productList != null
            category.productList.isEmpty()
    }

    void 'test not-joined collection should be null'() {
        given:
            Category category = new Category(name: "Cats", productList: [])
        when:
            categoryRepository.save(category)
            category = categoryRepository.queryById(category.id).get()
        then:
            category.productList == null
    }

}

@JdbcRepository(dialect = Dialect.H2)
interface CategoryRepository extends CrudRepository<Category, Long> {

    @Join(value = "productList", alias =  "p_", type =  Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption", alias =  "op_", type =  Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption.option", alias =  "opno_", type =  Join.Type.LEFT_FETCH)
    @Override
    List<Category> findAll()

    @Join(value = "productList", alias =  "p_", type =  Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption", alias =  "op_", type =  Join.Type.LEFT_FETCH)
    @Join(value = "productList.productOption.option", alias =  "opno_", type =  Join.Type.LEFT_FETCH)
    @Override
    Optional<Category> findById(Long id);

    Optional<Category> queryById(Long id);
}

@MappedEntity("mo2m_category")
class Category {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "category", cascade = Relation.Cascade.ALL)
    List<Product> productList
}

@MappedEntity("mo2m_product")
class Product {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    Category category
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "product", cascade = Relation.Cascade.ALL)
    List<ProductOption> productOption
}

@MappedEntity("mo2m_product_option")
class ProductOption {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    Product product
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "productOption", cascade = Relation.Cascade.ALL)
    List<Option> option
}

@MappedEntity("mo2m_option")
class Option {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    ProductOption productOption
}