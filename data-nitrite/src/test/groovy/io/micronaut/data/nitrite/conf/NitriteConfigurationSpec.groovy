package io.micronaut.data.nitrite.conf

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.AutoCleanup

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
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

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
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

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
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

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
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

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
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

        when:
        config.setStorageMode(NitriteConfiguration.StorageMode.MVSTORE)

        then:
        config.getStorageMode() == NitriteConfiguration.StorageMode.MVSTORE
    }

    void "test setStorageMode IN_MEMORY"() {
        given:
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

        when:
        config.setStorageMode(NitriteConfiguration.StorageMode.IN_MEMORY)

        then:
        config.getStorageMode() == NitriteConfiguration.StorageMode.IN_MEMORY
    }

    void "test setStorageMode ROCKSDB"() {
        given:
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

        when:
        config.setStorageMode(NitriteConfiguration.StorageMode.ROCKSDB)

        then:
        config.getStorageMode() == NitriteConfiguration.StorageMode.ROCKSDB
    }

    void "test setFieldSeparator"() {
        given:
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

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
        ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

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
            "nitrite.storage-mode": "MVSTORE",
            "nitrite.db-path": "/data/myapp.db",
            "nitrite.username": "admin",
            "nitrite.password": "secret"
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
            "nitrite.storage-mode": "IN_MEMORY"
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
            "nitrite.field-separator": "_",
            "nitrite.create-indexes": "false"
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
}
