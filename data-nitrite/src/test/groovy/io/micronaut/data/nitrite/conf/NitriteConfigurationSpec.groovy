package io.micronaut.data.nitrite.conf

import io.micronaut.context.ApplicationContext
import org.dizitart.no2.Nitrite
import org.dizitart.no2.collection.Document
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Files

import static org.dizitart.no2.filters.FluentFilter.where

/**
 * Tests for NitriteConfiguration binding and setter methods.
 *
 * This spec covers:
 * - All setter methods (setDbPath, setUsername, setPassword, setStorageMode, setSortedReadStrategy,
 *   setFieldSeparator, setCreateIndexes)
 * - All StorageMode enum values (MVSTORE, IN_MEMORY, ROCKSDB)
 * - Configuration binding from properties
 */
class NitriteConfigurationSpec extends Specification {

    @AutoCleanup ApplicationContext ctx

    void "test configuration binding from properties - MVSTORE with db-path"() {
        given:
        def props = [
            "micronaut.nitrite.default.storage-mode": "MVSTORE",
            "micronaut.nitrite.default.db-path": "/data/myapp.db",
            "micronaut.nitrite.default.username": "admin",
            "micronaut.nitrite.default.password": "secret"
        ]
        ctx = ApplicationContext.run(props)
        def config = ctx.getBean(NitriteConfiguration)

        expect:
        config.getStorageMode() == NitriteConfiguration.StorageMode.MVSTORE
        config.getDbPath() == "/data/myapp.db"
        config.getUsername() == "admin"
        config.getPassword() == "secret"
    }

    void "the MVStore page split size binds and defaults to the adapter's own value"() {
        given:
        ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "MVSTORE",
            "micronaut.nitrite.default.db-path": "/data/myapp.db",
            "micronaut.nitrite.default.mvstore-page-split-size": 16384
        ])

        expect:
        ctx.getBean(NitriteConfiguration).getMvstorePageSplitSize() == 16384

        when: "the property is absent"
        ctx.close()
        ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "MVSTORE",
            "micronaut.nitrite.default.db-path": "/data/myapp.db"
        ])

        then: "nothing is passed to the module builder"
        ctx.getBean(NitriteConfiguration).getMvstorePageSplitSize() == null
    }

    void "every MVStore setting binds, and an unset one stays null"() {
        given:
        ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "MVSTORE",
            "micronaut.nitrite.default.db-path": "/data/myapp.db",
            "micronaut.nitrite.default.mvstore-cache-size": 64,
            "micronaut.nitrite.default.mvstore-cache-concurrency": 8,
            "micronaut.nitrite.default.mvstore-auto-commit": false,
            "micronaut.nitrite.default.mvstore-auto-commit-buffer-size": 4096,
            "micronaut.nitrite.default.mvstore-auto-compact": false,
            "micronaut.nitrite.default.mvstore-compress": true,
            "micronaut.nitrite.default.mvstore-compress-high": true,
            "micronaut.nitrite.default.mvstore-recovery-mode": true,
            "micronaut.nitrite.default.mvstore-read-only": true
        ])
        def config = ctx.getBean(NitriteConfiguration)

        expect:
        config.getMvstoreCacheSize() == 64
        config.getMvstoreCacheConcurrency() == 8
        !config.getMvstoreAutoCommit()
        config.getMvstoreAutoCommitBufferSize() == 4096
        !config.getMvstoreAutoCompact()
        config.getMvstoreCompress()
        config.getMvstoreCompressHigh()
        config.getMvstoreRecoveryMode()
        config.getMvstoreReadOnly()

        and: "the one setting this datasource does not name is left to the adapter"
        config.getMvstorePageSplitSize() == null
    }

    void "an encrypted MVStore datasource round-trips a document and rejects the wrong key"() {
        given:
        def dbPath = Files.createTempDirectory("nitrite-encrypted").resolve("bodies.db")
        def props = { String key ->
            [
                "micronaut.nitrite.default.storage-mode": "MVSTORE",
                "micronaut.nitrite.default.db-path"     : dbPath.toString(),
                "micronaut.nitrite.default.mvstore-encryption-key": key
            ]
        }
        ctx = ApplicationContext.run(props("correct-horse"))

        when:
        def collection = ctx.getBean(Nitrite).getCollection("bodies")
        collection.insert(Document.createDocument("id", "one").put("body", "hello"))
        ctx.close()

        then: "the key opens the store again"
        def reopened = ApplicationContext.run(props("correct-horse"))
        reopened.getBean(Nitrite).getCollection("bodies")
                .find(where("id").eq("one")).firstOrNull().get("body") == "hello"
        reopened.close()

        when: "a different key is supplied"
        def wrongKey = ApplicationContext.run(props("wrong-key"))
        wrongKey.getBean(Nitrite)

        then:
        thrown(Exception)

        cleanup:
        wrongKey?.close()
        Files.deleteIfExists(dbPath)
    }

    void "a datasource carrying every MVStore setting opens and round-trips a document"() {
        given:
        def dbPath = Files.createTempDirectory("nitrite-mvstore-settings").resolve("bodies.db")
        ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "MVSTORE",
            "micronaut.nitrite.default.db-path": dbPath.toString(),
            "micronaut.nitrite.default.mvstore-page-split-size": 16384,
            "micronaut.nitrite.default.mvstore-cache-size": 32,
            "micronaut.nitrite.default.mvstore-cache-concurrency": 8,
            "micronaut.nitrite.default.mvstore-auto-commit-buffer-size": 4096,
            "micronaut.nitrite.default.mvstore-auto-compact": false,
            "micronaut.nitrite.default.mvstore-compress": true
        ])

        when:
        def collection = ctx.getBean(Nitrite).getCollection("bodies")
        collection.insert(Document.createDocument("id", "one").put("body", "hello"))

        then:
        collection.find(where("id").eq("one")).firstOrNull().get("body") == "hello"

        cleanup:
        ctx.close()
        Files.deleteIfExists(dbPath)
    }

    void "a datasource configured with a page split size opens and round-trips a document"() {
        given:
        def dbPath = Files.createTempDirectory("nitrite-page-split").resolve("bodies.db")
        ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "MVSTORE",
            "micronaut.nitrite.default.db-path": dbPath.toString(),
            "micronaut.nitrite.default.mvstore-page-split-size": 16384
        ])

        when:
        def collection = ctx.getBean(Nitrite).getCollection("bodies")
        collection.insert(Document.createDocument("id", "one").put("body", "hello"))

        then:
        collection.find(where("id").eq("one")).firstOrNull().get("body") == "hello"

        cleanup:
        ctx.close()
        Files.deleteIfExists(dbPath)
    }

    void "test configuration binding from properties - IN_MEMORY"() {
        given:
        def props = [
            "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
        ]
        ctx = ApplicationContext.run(props)
        def config = ctx.getBean(NitriteConfiguration)

        expect:
        config.getStorageMode() == NitriteConfiguration.StorageMode.IN_MEMORY
        config.getDbPath() == null
    }

    void "test configuration binding for sorted-read strategy"() {
        given:
        ctx = ApplicationContext.run([
                "micronaut.nitrite.default.sorted-read-strategy": "DATABASE"
        ])

        expect:
        ctx.getBean(NitriteConfiguration).getSortedReadStrategy() == NitriteConfiguration.SortedReadStrategy.DATABASE
    }

    @Unroll
    void "sorted-read strategy #strategy for #storageMode uses cursor limit: #usesCursorLimit"() {
        expect:
        strategy.usesCursorLimit(storageMode) == usesCursorLimit

        where:
        strategy                                       | storageMode                                  | usesCursorLimit
        NitriteConfiguration.SortedReadStrategy.AUTO   | NitriteConfiguration.StorageMode.MVSTORE   | true
        NitriteConfiguration.SortedReadStrategy.AUTO   | NitriteConfiguration.StorageMode.IN_MEMORY | true
        NitriteConfiguration.SortedReadStrategy.AUTO   | NitriteConfiguration.StorageMode.ROCKSDB   | false
        NitriteConfiguration.SortedReadStrategy.CURSOR | NitriteConfiguration.StorageMode.ROCKSDB   | true
        NitriteConfiguration.SortedReadStrategy.DATABASE | NitriteConfiguration.StorageMode.MVSTORE | false
    }

    void "test configuration binding from properties - custom field separator"() {
        given:
        def props = [
            "micronaut.nitrite.default.field-separator": "_",
            "micronaut.nitrite.default.create-indexes": "false"
        ]
        ctx = ApplicationContext.run(props)
        def config = ctx.getBean(NitriteConfiguration)

        expect:
        config.getFieldSeparator() == "_"
        !config.isCreateIndexes()
    }

    void "named datasource configuration creates an isolated Nitrite configuration"() {
        given:
        ctx = ApplicationContext.run([
                "micronaut.nitrite.primary.storage-mode": "IN_MEMORY",
                "micronaut.nitrite.audit.storage-mode": "IN_MEMORY"
        ])

        expect:
        ctx.getBeansOfType(NitriteConfiguration)*.name.findAll { it in ["primary", "audit"] }.sort() == ["audit", "primary"]
    }

}
