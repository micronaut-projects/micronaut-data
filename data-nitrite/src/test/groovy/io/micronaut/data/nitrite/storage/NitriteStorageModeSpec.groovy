package io.micronaut.data.nitrite.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.Book
import io.micronaut.data.nitrite.repository.BookRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files

class NitriteStorageModeSpec extends Specification {

    @Shared
    File tempDir

    def setupSpec() {
        tempDir = Files.createTempDirectory("nitrite-storage-test").toFile()
    }

    def cleanupSpec() {
        tempDir?.deleteDir()
    }

    @Unroll
    void "test storage mode: #mode"() {
        given:
        Map<String, Object> props = [
            "nitrite.storage-mode": mode
        ]
        if (mode != "IN_MEMORY" && mode != "DEFAULT_IN_MEMORY") {
            props["nitrite.db-path"] = new File(tempDir, "${mode.toLowerCase()}.db").absolutePath
        }
        if (mode == "DEFAULT_IN_MEMORY") {
            props["nitrite.storage-mode"] = "MVSTORE"
            // No db-path
        }

        when:
        def ctx = ApplicationContext.run(props)
        def repository = ctx.getBean(BookRepository)
        def saved = repository.save(new Book("Mode: $mode"))

        then:
        saved.id
        repository.findById(saved.id).get().title == "Mode: $mode"

        cleanup:
        ctx.close()

        where:
        mode << ["MVSTORE", "IN_MEMORY", "DEFAULT_IN_MEMORY"]
    }
}
