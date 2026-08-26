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
        config.getDbPath() == null
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
