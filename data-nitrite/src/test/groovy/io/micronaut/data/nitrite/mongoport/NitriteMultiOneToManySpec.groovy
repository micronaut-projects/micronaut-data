package io.micronaut.data.nitrite.mongoport

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.mongoport.entities.NitriteCategory
import io.micronaut.data.nitrite.mongoport.entities.NitriteOption
import io.micronaut.data.nitrite.mongoport.entities.NitriteProduct
import io.micronaut.data.nitrite.mongoport.entities.NitriteProductOption
import io.micronaut.data.nitrite.mongoport.repositories.NitriteCategoryRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class NitriteMultiOneToManySpec extends Specification implements NitriteTestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    NitriteCategoryRepository categoryRepository = applicationContext.getBean(NitriteCategoryRepository)

    def cleanup() {
        categoryRepository.deleteAll()
    }

    void 'test handle null values in joins'() {
        expect:"no results are returned"
        categoryRepository.findAll().isEmpty()
    }

    void 'test one-to-many hierarchy'() {
        given:
            NitriteCategory category = new NitriteCategory(name: "Cats", productList: [
                    new NitriteProduct(name: "Food", productOption: [
                            new NitriteProductOption(name: "Pork", option: [new NitriteOption(name: "X"), new NitriteOption(name: "Y"), new NitriteOption(name: "Z")]),
                            new NitriteProductOption(name: "Beef", option: [new NitriteOption(name: "A"), new NitriteOption(name: "B"), new NitriteOption(name: "C")])
                    ]),
                    new NitriteProduct(name: "Toys", productOption:  [
                            new NitriteProductOption(name: "Ffff", option: [new NitriteOption(name: "F1"), new NitriteOption(name: "F2"), new NitriteOption(name: "F3")]),
                            new NitriteProductOption(name: "Pfff", option: [new NitriteOption(name: "P1"), new NitriteOption(name: "P2"), new NitriteOption(name: "P3")])
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
            category = categoryRepository.listAll().first()
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

    void 'test not-joined collection should be null'() {
        given:
            NitriteCategory category = new NitriteCategory(name: "Cats", productList: [])
        when:
            categoryRepository.save(category)
            category = categoryRepository.queryById(category.id).get()
        then:
            category.productList == null
    }

    void 'test joined collection should not be null'() {
        given:
            NitriteCategory category = new NitriteCategory(name: "Cats", productList: [])
        when:
            categoryRepository.save(category)
            category = categoryRepository.findById(category.id).get()
        then:
            category.productList != null
            category.productList.isEmpty()
    }
}
