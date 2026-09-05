package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.data.nitrite.conf.NitriteConfiguration
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.serde.ObjectMapper
import org.dizitart.no2.Nitrite
import spock.lang.Specification
import java.nio.file.Files

class NitriteOperationsFactorySpec extends Specification {

    void "default datasource starts with default configuration without explicit properties"() {
        when:
        def ctx = ApplicationContext.run()
        def config = ctx.getBean(NitriteConfiguration)

        then:
        config.name == "default"
        config.dbPath == null
        config.storageMode == NitriteConfiguration.StorageMode.MVSTORE
        config.fieldSeparator == "."
        config.createIndexes
        config.username == null
        config.password == null
        ctx.getBean(Nitrite) != null
        ctx.getBean(NitriteRepositoryOperations) != null

        cleanup:
        ctx?.close()
    }

    void "each named repository operations bean uses its matching configuration"() {
        when:
        def ctx = ApplicationContext.run([
                "micronaut.nitrite.default.storage-mode": "IN_MEMORY",
                "micronaut.nitrite.default.create-indexes": true,
                "micronaut.nitrite.audit.storage-mode": "IN_MEMORY",
                "micronaut.nitrite.audit.create-indexes": false
        ])
        def defaultOperations = ctx.getBean(NitriteRepositoryOperations, Qualifiers.byName("default"))
        def auditOperations = ctx.getBean(NitriteRepositoryOperations, Qualifiers.byName("audit"))

        then:
        configurationOf(defaultOperations).name == "default"
        configurationOf(auditOperations).name == "audit"

        cleanup:
        ctx?.close()
    }

    void "test nitrite repository operations without serde object mapper"() {
        when:
        def ctx = ApplicationContext.builder(["micronaut.nitrite.default.storage-mode": "IN_MEMORY"])
            .classLoader(new HidingClassLoader(Thread.currentThread().contextClassLoader, "io.micronaut.serde.jackson"))
            .start()

        then:
        !ctx.containsBean(ObjectMapper)
        ctx.getBean(NitriteRepositoryOperations) != null

        cleanup:
        ctx?.close()
    }

    void "test nitrite factory empty dbPath with MVSTORE"() {
        when: "Empty dbPath with MVSTORE (default)"
        def ctx = ApplicationContext.run(["micronaut.nitrite.default.db-path": ""])

        then:
        ctx.getBean(Nitrite) != null

        cleanup:
        ctx?.close()
    }
    void "test nitrite factory empty dbPath with ROCKSDB"() {
        when: "Empty dbPath with ROCKSDB"
        def ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "ROCKSDB",
            "micronaut.nitrite.default.db-path": ""
        ])
        ctx.getBean(Nitrite)
        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IllegalStateException
        e.cause.message.contains("RocksDB storage mode requires a valid micronaut.nitrite.default.db-path")
        cleanup:
        ctx?.close()
    }

    void "test nitrite factory ROCKSDB without library"() {
        given: "the RocksDB adapter is absent from this source set's runtime classpath"
        def tempDir = Files.createTempDirectory("nitrite-factory-rocks-missing").toFile()
        def dbFile = new File(tempDir, "rocks.db")

        when: "ROCKSDB mode is configured without the adapter"
        def ctx = ApplicationContext.run([
            "micronaut.nitrite.default.storage-mode": "ROCKSDB",
            "micronaut.nitrite.default.db-path": dbFile.absolutePath
        ])
        ctx.getBean(Nitrite)

        then: "startup fails naming the missing adapter instead of silently using another store"
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IllegalStateException
        e.cause.message.contains("org.dizitart.no2.rocksdb.RocksDBModule")

        cleanup:
        ctx?.close()
        tempDir.deleteDir()
    }

    void "test nitrite factory with auth"() {
        when: "With username and password"
        def ctx3 = ApplicationContext.run([
            "micronaut.nitrite.default.username": "admin",
            "micronaut.nitrite.default.password": "password"
        ])
        then:
        ctx3.getBean(Nitrite) != null
        cleanup:
        ctx3?.close()
    }

    void "test nitrite factory with explicit path and nested dirs"() {
        given:
        def tempDir = Files.createTempDirectory("nitrite-factory-test").toFile()

        when: "MVSTORE with explicit path"
        def dbFile = new File(tempDir, "test.db")
        def ctx4 = ApplicationContext.run(["micronaut.nitrite.default.db-path": dbFile.absolutePath])

        then:
        ctx4.getBean(Nitrite) != null
        dbFile.exists()

        when: "Nested directory creation"
        def nestedFile = new File(tempDir, "nested/dir/test.db")
        def ctx5 = ApplicationContext.run(["micronaut.nitrite.default.db-path": nestedFile.absolutePath])

        then:
        ctx5.getBean(Nitrite) != null
        nestedFile.exists()

        when: "Path with no parent"
        def ctx6 = ApplicationContext.run(["micronaut.nitrite.default.db-path": "standalone.db"])

        then:
        ctx6.getBean(Nitrite) != null
        new File("standalone.db").exists()

        cleanup:
        ctx4?.close()
        ctx5?.close()
        ctx6?.close()
        new File("standalone.db").delete()
        tempDir.deleteDir()
    }

    private static final class HidingClassLoader extends ClassLoader {
        private final String hiddenPackage

        HidingClassLoader(ClassLoader parent, String hiddenPackage) {
            super(parent)
            this.hiddenPackage = hiddenPackage
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(hiddenPackage)) {
                throw new ClassNotFoundException(name)
            }
            return super.loadClass(name, resolve)
        }

        @Override
        URL getResource(String name) {
            if (name.contains(hiddenPackage.replace('.', '/'))) {
                return null
            }
            return super.getResource(name)
        }

        @Override
        Enumeration<URL> getResources(String name) throws IOException {
            if (name.contains(hiddenPackage.replace('.', '/'))) {
                return Collections.emptyEnumeration()
            }
            return super.getResources(name)
        }
    }

    private static NitriteConfiguration configurationOf(NitriteRepositoryOperations operations) {
        def registryField = DefaultNitriteRepositoryOperations.getDeclaredField("collectionRegistry")
        registryField.accessible = true
        def registry = registryField.get(operations)
        def configurationField = registry.class.getDeclaredField("configuration")
        configurationField.accessible = true
        return (NitriteConfiguration) configurationField.get(registry)
    }

    void "one configured datasource creates exactly one repository operations bean"() {
        when:
        def ctx = ApplicationContext.run([
                "micronaut.nitrite.default.storage-mode": "IN_MEMORY"
        ])

        then:
        ctx.getBeansOfType(NitriteRepositoryOperations).size() == 1

        cleanup:
        ctx?.close()
    }
}
