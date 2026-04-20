package io.micronaut.data.jdbc.h2

import groovy.transform.Memoized
import io.micronaut.data.tck.entities.*
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.metamodel.AbstractMetamodelSpec

class JdbcRepositorySpec extends AbstractMetamodelSpec implements H2TestPropertyProvider {

    @Override
    @Memoized
    BookRepository getBookRepository() {
        return context.getBean(H2BookRepository)
    }

    @Memoized
    AuthenticationRepository getAuthenticationRepository() {
        return context.getBean(H2AuthenticationRepository)
    }

    @Override
    @Memoized
    BasicTypesRepository getBasicTypeRepository() {
        return context.getBean(H2BasicTypesRepository)
    }

    @Override
    @Memoized
    ChildRepository getChildRepository() {
        return context.getBean(H2ChildRepository)
    }

    @Override
    @Memoized
    EmbeddedOwnerRepository getEmbeddedOwnerRepository() {
        return context.getBean(H2EmbeddedOwnerRepository)
    }

    @Override
    EmployeePropertyAccessRepository getEmployeePropertyAccessRepository() {
        return context.getBean(H2EmployeePropertyAccessRepository)
    }

    @Override
    @Memoized
    EmployeeFieldAccessRepository getEmployeeFieldAccessRepository() {
        return context.getBean(H2EmployeeFieldAccessRepository)
    }

    @Override
    @Memoized
    EmployeeMixedAccessRepository getEmployeeMixedAccessRepository() {
        return context.getBean(H2EmployeeMixedAccessRepository)
    }

    @Override
    @Memoized
    EmployeeMixedAccessEmbeddedIdRepository getEmployeeMixedAccessEmbeddedIdRepository() {
        return context.getBean(H2EmployeeMixedAccessEmbeddedIdRepository)
    }

    @Override
    @Memoized
    PurchaseOrderRepository getPurchaseOrderRepository() {
        return context.getBean(H2PurchaseOrderRepository)
    }

    @Override
    @Memoized
    AuthorRepository getAuthorRepository() {
        return context.getBean(H2AuthorRepository)
    }

    @Override
    @Memoized
    GenreRepository getGenreRepository() {
        return context.getBean(H2GenreRepository)
    }

    @Override
    @Memoized
    PublisherRepository getPublisherRepository() {
        return context.getBean(H2PublisherRepository)
    }

    @Override
    @Memoized
    ClientRepository getClientRepository() {
        return context.getBean(H2ClientRepository)
    }

    @Override
    @Memoized
    ClientCategoryRepository getClientCategoryRepository() {
        return context.getBean(H2ClientCategoryRepository)
    }

    @Memoized
    DeviceRepository getDeviceRepository() {
        return context.getBean(H2DeviceRepository)
    }

    @Override
    @Memoized
    TrainRepository getTrainRepository() {
        return context.getBean(H2TrainRepository)
    }

    @Override
    @Memoized
    ChapterRepository getChapterRepository() {
        return context.getBean(H2ChapterRepository)
    }

    @Override
    PageRepository getPageRepository() {
        return context.getBean(H2PageRepository)
    }

    @Override
    void populateClientAndCategories() {
        def fiction = new ClientCategory("Fiction", null, new byte[]{})
        def sciFi = new ClientCategory("Sci-Fi", null, new byte[]{})
        def history = new ClientCategory("History", null, new byte[]{})
        def main = new ClientCategory("Main", null, new byte[]{})
        clientCategoryRepository.saveAll([fiction, sciFi, history, main])

        def c = new Client()
        c.id = 3L
        c.name = "Carol"
        c.billingAddress = new Client.Address("street", "city")
        c.categoriesSet.add(history)
        c.categoriesList.addAll([fiction, sciFi])
        c.mainCategory = main
        clientRepository.save(c)
    }

    void "can join Authentication to Device and filter by Device name"() {
        given:
        def d1 = new Device()
        def user = new User()
        user.name = "Mohammed"
        d1.name = "Phone"
        d1.setUser(user)
        def d2 = new Device()
        d2.name = "Tablet"
        d2.setUser(user)

        deviceRepository.saveAll([d1, d2])

        def a1 = new Authentication()
        a1.description = "auth-1"
        a1.device = d1

        def a2 = new Authentication()
        a2.description = "auth-2"
        a2.device = d2

        authenticationRepository.saveAll([a1, a2])

        when:
        def result = authenticationRepository.findAll(AuthenticationRepository.Specification.withDeviceName("Phone"))

        then:
        result.size() == 1
        result.first().id != null
        result.first().description == "auth-1"
        result.first().device != null
        result.first().device.name == "Phone"
    }
}
