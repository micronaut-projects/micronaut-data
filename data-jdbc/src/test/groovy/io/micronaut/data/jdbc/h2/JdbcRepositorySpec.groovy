package io.micronaut.data.jdbc.h2

import groovy.transform.Memoized
import io.micronaut.data.tck.repositories.*
import io.micronaut.data.tck.tests.AbstractMetamodelSpec

class JdbcRepositorySpec extends AbstractMetamodelSpec implements H2TestPropertyProvider {

    @Override
    @Memoized
    BookRepository getBookRepository() {
        return context.getBean(H2BookRepository)
    }

    @Override
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
    AuthorRepository getAuthorRepository() {
        return context.getBean(H2AuthorRepository)
    }

    @Override
    GenreRepository getGenreRepository() {
        return context.getBean(H2GenreRepository)
    }

    @Override
    PublisherRepository getPublisherRepository() {
        return context.getBean(H2PublisherRepository)
    }

    @Override
    ClientRepository getClientRepository() {
        return context.getBean(H2ClientRepository)
    }

    @Override
    ClientCategoryRepository getClientCategoryRepository() {
        return context.getBean(H2ClientCategoryRepository)
    }

    @Override
    DeviceRepository getDeviceRepository() {
        return context.getBean(H2DeviceRepository)
    }

    @Override
    TrainRepository getTrainRepository() {
        return context.getBean(H2TrainRepository)
    }
}
