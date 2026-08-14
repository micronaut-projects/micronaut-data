package io.micronaut.data.nitrite

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.TimestampedRecord
import io.micronaut.data.nitrite.repository.TimestampedRecordRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class NitriteUpsertLifecycleSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run([
        "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
    ])

    @Shared
    TimestampedRecordRepository repo = context.getBean(TimestampedRecordRepository)

    def setup() {
        repo.deleteAll()
    }

    void "test save() on new entity sets created and updated timestamps"() {
        when:
        def record = new TimestampedRecord("new")
        def saved = repo.save(record)

        then:
        saved.id != null
        saved.version == 0L
        saved.dateCreated != null
        saved.dateUpdated != null
        saved.dateCreated == saved.dateUpdated

        when:
        sleep(100) // Ensure time passes
        def originalDateUpdated = saved.dateUpdated
        saved.name = "updated"
        def updated = repo.save(saved)

        then:
        updated.id == saved.id
        updated.version == 1L
        updated.dateCreated == saved.dateCreated
        updated.dateUpdated > originalDateUpdated
    }

    void "test veto removes entity from batch before persist"() {
        when:
        def r1 = new TimestampedRecord("keep")
        def r2 = new TimestampedRecord("veto-me")
        repo.saveAll([r1, r2])

        then:
        def all = repo.findAll().toList()
        all.size() == 1
        all[0].name == "keep"
    }

    void "test saveAll() on mixed entities sets correct timestamps and versions"() {
        given:
        def existing = repo.save(new TimestampedRecord("existing"))
        def initialCreated = existing.dateCreated
        def initialUpdated = existing.dateUpdated
        sleep(100)

        when:
        existing.name = "existing-updated"
        def newRecord = new TimestampedRecord("new")
        repo.saveAll([existing, newRecord])

        then:
        def reloadedExisting = repo.findById(existing.id).get()
        reloadedExisting.version == 1L
        reloadedExisting.dateCreated == initialCreated
        reloadedExisting.dateUpdated > initialUpdated

        def reloadedNew = repo.findAll().find { it.name == "new" }
        reloadedNew != null
        reloadedNew.version == 0L
        reloadedNew.dateCreated != null
        reloadedNew.dateCreated == reloadedNew.dateUpdated
    }
}
