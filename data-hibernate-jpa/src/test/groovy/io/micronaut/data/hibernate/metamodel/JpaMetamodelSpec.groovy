package io.micronaut.data.hibernate.metamodel

import groovy.transform.Memoized
import io.micronaut.data.hibernate.HibernateTrainRepository
import io.micronaut.data.hibernate.JpaBasicTypesRepository
import io.micronaut.data.hibernate.JpaChapterRepository
import io.micronaut.data.hibernate.JpaChildRepository
import io.micronaut.data.hibernate.JpaClientCategoryRepository
import io.micronaut.data.hibernate.JpaClientRepository
import io.micronaut.data.hibernate.JpaEmbeddedOwnerRepository
import io.micronaut.data.hibernate.JpaEmployeeFieldAccessRepository
import io.micronaut.data.hibernate.JpaEmployeeMixedAccessEmbeddedIdRepository
import io.micronaut.data.hibernate.JpaEmployeeMixedAccessRepository
import io.micronaut.data.hibernate.JpaEmployeePropertyAccessRepository
import io.micronaut.data.hibernate.JpaEntityWithMapFieldRepository
import io.micronaut.data.hibernate.JpaGenreRepository
import io.micronaut.data.hibernate.JpaPageRepository
import io.micronaut.data.hibernate.JpaPublisherRepository
import io.micronaut.data.hibernate.JpaPurchaseOrderRepository
import io.micronaut.data.hibernate.entities.EntityWithMapField
import io.micronaut.data.tck.entities.Client
import io.micronaut.data.tck.entities.ClientCategory
import io.micronaut.data.tck.repositories.AuthorRepository
import io.micronaut.data.tck.repositories.BasicTypesRepository
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.data.tck.repositories.ChapterRepository
import io.micronaut.data.tck.repositories.ChildRepository
import io.micronaut.data.tck.repositories.ClientCategoryRepository
import io.micronaut.data.tck.repositories.ClientRepository
import io.micronaut.data.tck.repositories.EmbeddedOwnerRepository
import io.micronaut.data.tck.repositories.EmployeeFieldAccessRepository
import io.micronaut.data.tck.repositories.EmployeeMixedAccessEmbeddedIdRepository
import io.micronaut.data.tck.repositories.EmployeeMixedAccessRepository
import io.micronaut.data.tck.repositories.EmployeePropertyAccessRepository
import io.micronaut.data.tck.repositories.GenreRepository
import io.micronaut.data.tck.repositories.PageRepository
import io.micronaut.data.tck.repositories.PublisherRepository
import io.micronaut.data.tck.repositories.PurchaseOrderRepository
import io.micronaut.data.tck.repositories.TrainRepository
import io.micronaut.data.tck.tests.metamodel.AbstractMetamodelSpec

import static io.micronaut.data.hibernate.JpaEntityWithMapFieldRepository.Specification.hasTagInList
import static io.micronaut.data.hibernate.JpaEntityWithMapFieldRepository.Specification.hasTagInSet
import static io.micronaut.data.hibernate.JpaEntityWithMapFieldRepository.Specification.propertyEquals

class JpaMetamodelSpec extends AbstractMetamodelSpec implements H2TestPropertyProvider {

    @Override
    @Memoized
    GenreRepository getGenreRepository() {
        return context.getBean(JpaGenreRepository);
    }

    @Override
    @Memoized
    BookRepository getBookRepository() {
        return context.getBean(BookRepository);
    }

    @Override
    @Memoized
    PublisherRepository getPublisherRepository() {
        return context.getBean(JpaPublisherRepository);
    }

    @Override
    @Memoized
    AuthorRepository getAuthorRepository() {
        return context.getBean(AuthorRepository);
    }

    @Override
    @Memoized
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(JpaBasicTypesRepository);
    }

    @Override
    @Memoized
    ChildRepository getChildRepository() {
        return context.getBean(JpaChildRepository);
    }

    @Override
    @Memoized
    EmbeddedOwnerRepository getEmbeddedOwnerRepository() {
        return context.getBean(JpaEmbeddedOwnerRepository);
    }

    @Override
    @Memoized
    EmployeePropertyAccessRepository getEmployeePropertyAccessRepository() {
        return context.getBean(JpaEmployeePropertyAccessRepository);
    }

    @Override
    @Memoized
    EmployeeFieldAccessRepository getEmployeeFieldAccessRepository() {
        return context.getBean(JpaEmployeeFieldAccessRepository);
    }

    @Override
    @Memoized
    EmployeeMixedAccessRepository getEmployeeMixedAccessRepository() {
        return context.getBean(JpaEmployeeMixedAccessRepository);
    }

    @Override
    @Memoized
    EmployeeMixedAccessEmbeddedIdRepository getEmployeeMixedAccessEmbeddedIdRepository() {
        return context.getBean(JpaEmployeeMixedAccessEmbeddedIdRepository);
    }

    @Override
    @Memoized
    ClientRepository getClientRepository() {
        return context.getBean(JpaClientRepository);
    }

    @Override
    @Memoized
    PurchaseOrderRepository getPurchaseOrderRepository() {
        return context.getBean(JpaPurchaseOrderRepository);
    }

    @Override
    @Memoized
    ClientCategoryRepository getClientCategoryRepository() {
        return context.getBean(JpaClientCategoryRepository);
    }

    @Override
    @Memoized
    TrainRepository getTrainRepository() {
        return context.getBean(HibernateTrainRepository);
    }

    @Override
    @Memoized
    ChapterRepository getChapterRepository() {
        return context.getBean(JpaChapterRepository)
    }

    @Override
    @Memoized
    PageRepository getPageRepository() {
        return context.getBean(JpaPageRepository)
    }

    @Memoized
    JpaEntityWithMapFieldRepository getEntityWithMapFieldRepository() {
        return context.getBean(JpaEntityWithMapFieldRepository)
    }

    void setup() {
        entityWithMapFieldRepository.deleteAll()
    }

    @Override
    void populateClientAndCategories() {
        def fiction = new ClientCategory("Fiction", null, new byte[]{})
        def sciFi = new ClientCategory("Sci-Fi", null, new byte[]{})
        def history = new ClientCategory("History", null, new byte[]{})
        def main = new ClientCategory("Main", null, new byte[]{})
        def c = new Client()
        c.id = 3L
        c.name = "Carol"
        c.billingAddress = new Client.Address("street", "city")
        c.categoriesList.addAll([fiction, sciFi])
        c.categoriesSet.add(history)
        c.mainCategory = main
        clientRepository.save(c)
    }

    void "can join map element collection and filter by key/value using static metamodel"() {
        given:
        def e1 = new EntityWithMapField()
        e1.id = 1L
        e1.properties = [region: "EMEA", segment: "ENT"] as Map<String, String>

        def e2 = new EntityWithMapField()
        e2.id = 2L
        e2.properties = [region: "US", segment: "FL"] as Map<String, String>

        def e3 = new EntityWithMapField()
        e3.id = 3L

        entityWithMapFieldRepository.saveAll([e1, e2, e3])

        when:
        var result = entityWithMapFieldRepository.findAll(
                propertyEquals("region", "EMEA")
        )

        then:
        result.size() == 1
        result.getFirst().id == 1
        result.getFirst().properties.containsKey("region")
    }

    void "can join list element collection and filter by tag"() {
        given:
        def e1 = new EntityWithMapField(id: 10L)
        e1.tagsList = ["a", "b", "x"]

        def e2 = new EntityWithMapField(id: 11L)
        e2.tagsList = ["c", "d"]

        entityWithMapFieldRepository.saveAll([e1, e2])

        when:
        def result = entityWithMapFieldRepository.findAll(
                hasTagInList('x'))

        then:
        result*.id == [10L]
        result.first().tagsList.contains("x")
    }

    void "can join set element collection and filter by tag"() {
        given:
        def e1 = new EntityWithMapField(id: 20L)
        e1.tagsSet = ["red", "blue"] as Set

        def e2 = new EntityWithMapField(id: 21L)
        e2.tagsSet = ["green"] as Set

        entityWithMapFieldRepository.saveAll([e1, e2])

        when:
        def result = entityWithMapFieldRepository.findAll(
                hasTagInSet("green"))

        then:
        result*.id == [21L]
        result.first().tagsSet.contains("green")
    }
}
