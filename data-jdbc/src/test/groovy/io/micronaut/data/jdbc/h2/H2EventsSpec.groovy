package io.micronaut.data.jdbc.h2

import io.micronaut.data.tck.repositories.DomainEventsReactiveRepository
import io.micronaut.data.tck.repositories.DomainEventsRepository
import io.micronaut.data.tck.tests.AbstractEventsSpec

class H2EventsSpec extends AbstractEventsSpec implements H2TestPropertyProvider {
    @Override
    DomainEventsRepository eventsRepository() {
        return context.getBean(H2DomainEventsRepository)
    }

    @Override
    DomainEventsReactiveRepository eventsReactiveRepository() {
        return context.getBean(H2DomainEventsReactiveRepository)
    }
}
