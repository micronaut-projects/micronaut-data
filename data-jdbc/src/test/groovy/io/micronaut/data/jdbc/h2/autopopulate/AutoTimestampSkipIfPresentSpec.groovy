package io.micronaut.data.jdbc.h2.autopopulate

import io.micronaut.context.ApplicationContext
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import jakarta.data.repository.Insert
import jakarta.data.repository.Repository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

class AutoTimestampSkipIfPresentSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    TSRepo repo = applicationContext.getBean(TSRepo)

    void 'date created skip if present on insert'() {
        when:
        def preset = Instant.parse("2020-01-01T00:00:00Z")
        def e = new TSEntity(null, preset, null)
        def saved = repo.save(e)
        then:
        saved.id
        saved.dateCreated == preset
        saved.dateUpdated
        cleanup:
        repo.deleteAll()
    }

    void 'date updated skipIfPresent applies only on insert, updates always refresh'() {
        when:
        def e = new TSEntity(null, null, null)
        def saved = repo.save(e)
        def before = saved.dateUpdated
        def customUpdate = Instant.parse("2021-01-02T03:04:05Z")
        def changed = new TSEntity(saved.id, saved.dateCreated, customUpdate)
        repo.update(changed)
        def found = repo.findById(saved.id).get()
        then:
        found.dateUpdated != customUpdate
        found.dateUpdated >= before
        cleanup:
        repo.deleteAll()
    }
}

@Repository
@JdbcRepository(dialect = Dialect.H2)
interface TSRepo {
    @Insert
    TSEntity save(TSEntity e)

    @jakarta.data.repository.Update
    void update(TSEntity e)

    @jakarta.data.repository.Find
    Optional<TSEntity> findById(@jakarta.data.repository.By(jakarta.data.repository.By.ID) UUID id)

    @jakarta.data.repository.Query("DELETE FROM ts_skip_ap")
    void deleteAll()
}
