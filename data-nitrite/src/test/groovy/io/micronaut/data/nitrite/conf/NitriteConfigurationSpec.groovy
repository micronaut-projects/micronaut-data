package io.micronaut.data.nitrite.conf

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Tests for NitriteConfiguration binding and setter methods.
 *
 * This spec covers:
 * - All setter methods (setDbPath, setUsername, setPassword, setStorageMode, setFieldSeparator, setCreateIndexes)
 * - All StorageMode enum values (MVSTORE, IN_MEMORY, ROCKSDB)
 * - Configuration binding from properties
 */
class NitriteConfigurationSpec extends Specification {

    @AutoCleanup ApplicationContext ctx

    void "test default configuration values"() {
        given:
        def config = new NitriteConfiguration("default")

        expect:
        config.getDbPath() == null
        config.getStorageMode() == NitriteConfiguration.StorageMode.MVSTORE
        config.getFieldSeparator() == "."
        config.isCreateIndexes() == true
        config.getUsername() == null
        config.getPassword() == null
    }

    void "test setDbPath"() {
        given:
        def config = new NitriteConfiguration("default")

        when:
        config.setDbPath("/data/test.db")

        then:
        config.getDbPath() == "/data/test.db"

        when:
        config.setDbPath(null)

        then:
        config.getDbPath() == null
    }

    void "test setUsername"() {
        given:
        def config = new NitriteConfiguration("default")

        when:
        config.setUsername("admin")

        then:
        config.getUsername() == "admin"

        when:
        config.setUsername(null)

        then:
        config.getUsername() == null
    }

    void "test setPassword"() {
        given:
        def config = new NitriteConfiguration("default")

        when:
        config.setPassword("secret")

        then:
        config.getPassword() == "secret"

        when:
        config.setPassword(null)

        then:
        config.getPassword() == null
    }

    void "test setStorageMode MVSTORE"() {
        given:
        def config = new NitriteConfiguration("default")

        when:
        config.setStorageMode(NitriteConfiguration.StorageMode.MVSTORE)

        then:
        config.getStorageMode() == NitriteConfiguration.StorageMode.MVSTORE
    }

    void "test setStorageMode IN_MEMORY"() {
        given:
        def config = new NitriteConfiguration("default")

        when:
        config.setStorageMode(NitriteConfiguration.StorageMode.IN_MEMORY)

        then:
        config.getStorageMode() == NitriteConfiguration.StorageMode.IN_MEMORY
    }

    void "test setStorageMode ROCKSDB"() {
        given:
        def config = new NitriteConfiguration("default")

        when:
        config.setStorageMode(NitriteConfiguration.StorageMode.ROCKSDB)

        then:
        config.getStorageMode() == NitriteConfiguration.StorageMode.ROCKSDB
    }

    void "test setFieldSeparator"() {
        given:
        def config = new NitriteConfiguration("default")

        when:
        config.setFieldSeparator("_")

        then:
        config.getFieldSeparator() == "_"

        when:
        config.setFieldSeparator("/")

        then:
        config.getFieldSeparator() == "/"
    }

    void "test setCreateIndexes"() {
        given:
        def config = new NitriteConfiguration("default")

        when:
        config.setCreateIndexes(false)

        then:
        !config.isCreateIndexes()

        when:
        config.setCreateIndexes(true)

        then:
        config.isCreateIndexes()
    }

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

    void "test configuration binding from properties - IN_MEMORY"() {
        given:
        def props = [
            "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
        ]
        ctx = ApplicationContext.run(props)
        def config = ctx.getBean(NitriteConfiguration)

        expect:
        config.getStorageMode() == NitriteConfiguration.StorageMode.IN_MEMORY
        config.getDbPath() == null  // No db-path for in-memory
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

    void "test StorageMode enum values exist"() {
        expect:
        NitriteConfiguration.StorageMode.MVSTORE != null
        NitriteConfiguration.StorageMode.IN_MEMORY != null
        NitriteConfiguration.StorageMode.ROCKSDB != null
    }

    void "test StorageMode valueOf"() {
        expect:
        NitriteConfiguration.StorageMode.valueOf("MVSTORE") == NitriteConfiguration.StorageMode.MVSTORE
        NitriteConfiguration.StorageMode.valueOf("IN_MEMORY") == NitriteConfiguration.StorageMode.IN_MEMORY
        NitriteConfiguration.StorageMode.valueOf("ROCKSDB") == NitriteConfiguration.StorageMode.ROCKSDB
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
