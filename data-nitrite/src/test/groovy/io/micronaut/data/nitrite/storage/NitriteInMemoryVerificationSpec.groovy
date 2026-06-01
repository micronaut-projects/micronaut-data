package io.micronaut.data.nitrite.storage

import io.micronaut.context.ApplicationContext
import io.micronaut.data.nitrite.model.Book
import io.micronaut.data.nitrite.repository.BookRepository
import spock.lang.Specification

import java.nio.file.Files

class NitriteInMemoryVerificationSpec extends Specification {

    void "verify IN_MEMORY mode creates no files even with a path property"() {
        given: "A non-existent directory"
        File nonExistentDir = Files.createTempDirectory("nitrite-verify").toFile()
        nonExistentDir.deleteDir()
        assert !nonExistentDir.exists()

        and: "Config set to IN_MEMORY but providing a path in that deleted dir"
        File dbFile = new File(nonExistentDir, "should-not-exist.db")
        Map<String, Object> props = [
            "nitrite.storage-mode": "IN_MEMORY",
            "nitrite.db-path": dbFile.absolutePath
        ]

        def ctx = ApplicationContext.run(props)
        def repository = ctx.getBean(BookRepository)

        when: "We perform a write"
        repository.save(new Book("Verification"))

        then: "The directory and file still should not exist on disk"
        !nonExistentDir.exists()
        !dbFile.exists()

        cleanup:
        ctx.close()
    }

    void "verify MVSTORE mode DOES create files"() {
        given: "A valid temp directory"
        File tempDir = Files.createTempDirectory("nitrite-mvstore-verify").toFile()
        File dbFile = new File(tempDir, "must-exist.db")
        Map<String, Object> props = [
            "nitrite.storage-mode": "MVSTORE",
            "nitrite.db-path": dbFile.absolutePath
        ]

        def ctx = ApplicationContext.run(props)
        def repository = ctx.getBean(BookRepository)

        when: "We perform a write"
        repository.save(new Book("Persistence"))

        then: "The file must exist on disk"
        dbFile.exists()
        dbFile.length() > 0

        cleanup:
        ctx.close()
        tempDir.deleteDir()
    }
}
