package io.micronaut.data.nitrite.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.data.nitrite.conf.NitriteConfiguration
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations
import io.micronaut.serde.ObjectMapper
import org.dizitart.no2.Nitrite
import spock.lang.Specification
import java.nio.file.Files

class NitriteOperationsFactorySpec extends Specification {

    void "test nitrite factory in-memory mode"() {
        when: "In-memory mode"
        def ctx = ApplicationContext.run(["nitrite.storage-mode": "IN_MEMORY"])
        then:
        ctx.getBean(Nitrite) != null
        cleanup:
        ctx?.close()
    }

    void "test nitrite repository operations without serde object mapper"() {
        when:
        def ctx = ApplicationContext.builder(["nitrite.storage-mode": "IN_MEMORY"])
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
        def ctx = ApplicationContext.run(["nitrite.db-path": ""])
        then:
        ctx.getBean(Nitrite) != null
        cleanup:
        ctx?.close()
    }

    void "test nitrite factory empty dbPath with ROCKSDB"() {
        when: "Empty dbPath with ROCKSDB"
        def ctx = ApplicationContext.run([
            "nitrite.storage-mode": "ROCKSDB",
            "nitrite.db-path": ""
        ])
        ctx.getBean(Nitrite)
        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IllegalStateException
        e.cause.message.contains("RocksDB storage mode requires a valid nitrite.db-path")
        cleanup:
        ctx?.close()
    }

    void "test nitrite factory ROCKSDB without library"() {
        given:
        def tempDir = Files.createTempDirectory("nitrite-factory-rocks-missing").toFile()
        def dbFile = new File(tempDir, "rocks.db")

        when: "ROCKSDB mode without library"
        def ctx = ApplicationContext.run([
            "nitrite.storage-mode": "ROCKSDB",
            "nitrite.db-path": dbFile.absolutePath
        ])
        then:
        ctx.getBean(Nitrite) != null
        
        cleanup:
        ctx?.close()
        tempDir.deleteDir()
    }

    void "test nitrite factory with auth"() {
        when: "With username and password"
        def ctx3 = ApplicationContext.run([
            "nitrite.username": "admin",
            "nitrite.password": "password"
        ])
        then:
        ctx3.getBean(Nitrite) != null
        cleanup:
        ctx3?.close()
    }

    void "test nitrite factory with partial auth"() {
        when: "Only username provided"
        def ctx = ApplicationContext.run(["nitrite.username": "admin"])
        then:
        ctx.getBean(Nitrite) != null
        cleanup:
        ctx?.close()
    }

    void "test nitrite factory with explicit path and nested dirs"() {
        given:
        def tempDir = Files.createTempDirectory("nitrite-factory-test").toFile()

        when: "MVSTORE with explicit path"
        def dbFile = new File(tempDir, "test.db")
        def ctx4 = ApplicationContext.run(["nitrite.db-path": dbFile.absolutePath])
        then:
        ctx4.getBean(Nitrite) != null
        dbFile.exists()

        when: "Nested directory creation"
        def nestedFile = new File(tempDir, "nested/dir/test.db")
        def ctx5 = ApplicationContext.run(["nitrite.db-path": nestedFile.absolutePath])
        then:
        ctx5.getBean(Nitrite) != null
        nestedFile.exists()

        when: "Path with no parent"
        def ctx6 = ApplicationContext.run(["nitrite.db-path": "standalone.db"])
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
}
