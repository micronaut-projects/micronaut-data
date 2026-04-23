package io.micronaut.data.jdbc.sqlite

import io.micronaut.data.tck.repositories.DomainEventsReactiveRepository
import io.micronaut.data.tck.repositories.DomainEventsRepository
import io.micronaut.data.tck.tests.AbstractEventsSpec

class SQLiteEventsSpec extends AbstractEventsSpec implements SQLiteTestPropertyProvider {
    @Override
    DomainEventsRepository eventsRepository() {
        return context.getBean(SQLiteDomainEventsRepository)
    }

    @Override
    DomainEventsReactiveRepository eventsReactiveRepository() {
        return context.getBean(SQLiteDomainEventsReactiveRepository)
    }
}
