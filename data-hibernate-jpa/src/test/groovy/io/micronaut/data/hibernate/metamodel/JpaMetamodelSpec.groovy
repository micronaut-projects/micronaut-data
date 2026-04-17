package io.micronaut.data.hibernate.metamodel

import groovy.transform.Memoized
import io.micronaut.data.hibernate.*
import io.micronaut.data.tck.entities.Client
import io.micronaut.data.tck.entities.ClientCategory
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractMetamodelSpec

@H2DBProperties
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
        return context.getBean(JpaTrainRepository);
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
}
