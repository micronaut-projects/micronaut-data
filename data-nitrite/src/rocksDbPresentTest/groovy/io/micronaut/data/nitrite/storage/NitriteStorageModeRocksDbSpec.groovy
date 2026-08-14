package io.micronaut.data.nitrite.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.Book
import io.micronaut.data.nitrite.repository.BookRepository
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files

class NitriteStorageModeRocksDbSpec extends Specification {

    @Shared
    File tempDir

    def setupSpec() {
        tempDir = Files.createTempDirectory("nitrite-rocksdb-test").toFile()
    }

    def cleanupSpec() {
        tempDir?.deleteDir()
    }

    void "test storage mode: ROCKSDB"() {
        given:
        def ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "ROCKSDB",
            "micronaut.nitrite.default.db-path": new File(tempDir, "rocksdb.db").absolutePath
        ])
        def repository = ctx.getBean(BookRepository)

        when:
        def saved = repository.save(new Book("Mode: ROCKSDB"))

        then:
        saved.id
        repository.findById(saved.id).get().title == "Mode: ROCKSDB"

        cleanup:
        ctx.close()
    }
}
