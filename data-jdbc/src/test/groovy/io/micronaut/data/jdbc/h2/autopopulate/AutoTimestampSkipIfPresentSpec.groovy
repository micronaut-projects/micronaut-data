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

    @Shared
    ImmutableTSRepo immutableRepo = applicationContext.getBean(ImmutableTSRepo)

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

    void 'date updated skip if present preserves preset value on insert'() {
        when:
        def preset = Instant.parse("2021-01-01T00:00:00Z")
        def e = new TSEntity(null, null, preset)
        def saved = repo.save(e)
        then:
        saved.id
        saved.dateUpdated == preset
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

    void 'immutable embedded timestamps preserve preset values and generate missing values on insert'() {
        when:
        def created = Instant.parse("2019-01-01T00:00:00Z")
        def updated = Instant.parse("2019-01-02T00:00:00Z")
        def saved = immutableRepo.save(new ImmutableTimestampedEntity(null, "immutable-ts-1", new ImmutableAuditTimestamps(created, updated)))
        then:
        saved.id
        saved.audit.dateCreated == created
        saved.audit.dateUpdated == updated

        when:
        def generated = immutableRepo.save(new ImmutableTimestampedEntity(null, "immutable-ts-2", new ImmutableAuditTimestamps(null, null)))
        then:
        generated.id
        generated.audit.dateCreated != null
        generated.audit.dateUpdated != null

        cleanup:
        immutableRepo.deleteAll()
    }

    void 'immutable embedded dateUpdated refreshes on update'() {
        when:
        def saved = immutableRepo.save(new ImmutableTimestampedEntity(null, "immutable-ts-3", new ImmutableAuditTimestamps(null, null)))
        def before = saved.audit.dateUpdated
        def customUpdate = Instant.parse("2021-01-03T03:04:05Z")
        immutableRepo.update(new ImmutableTimestampedEntity(saved.id, saved.name, new ImmutableAuditTimestamps(saved.audit.dateCreated, customUpdate)))
        def found = immutableRepo.findById(saved.id).get()
        then:
        found.audit.dateCreated == saved.audit.dateCreated
        found.audit.dateUpdated != customUpdate
        found.audit.dateUpdated >= before

        cleanup:
        immutableRepo.deleteAll()
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

@Repository
@JdbcRepository(dialect = Dialect.H2)
interface ImmutableTSRepo {
    @Insert
    ImmutableTimestampedEntity save(ImmutableTimestampedEntity e)

    @jakarta.data.repository.Update
    void update(ImmutableTimestampedEntity e)

    @jakarta.data.repository.Find
    Optional<ImmutableTimestampedEntity> findById(@jakarta.data.repository.By(jakarta.data.repository.By.ID) UUID id)

    @jakarta.data.repository.Query("DELETE FROM immutable_ts_skip_ap")
    void deleteAll()
}
