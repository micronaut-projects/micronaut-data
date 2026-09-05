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

    void "test veto blocks a single save() of a new entity"() {
        when:
        repo.save(new TimestampedRecord("veto-me"))

        then:
        repo.findAll().toList().isEmpty()
    }

}
