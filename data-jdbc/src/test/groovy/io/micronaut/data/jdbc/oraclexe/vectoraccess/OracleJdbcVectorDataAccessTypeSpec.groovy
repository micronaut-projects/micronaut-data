package io.micronaut.data.jdbc.oraclexe.vectoraccess

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.convert.DataAccessType
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.repository.CrudRepository
import jakarta.inject.Singleton
import oracle.sql.VECTOR
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The write and read paths of JDBC repositories must ignore vector converters of another driver API, such as
 * the R2DBC ones when both modules are on the classpath.
 */
class OracleJdbcVectorDataAccessTypeSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ['spec.name': 'OracleJdbcVectorDataAccessTypeSpec'])

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void "a JDBC save and read never use an R2DBC vector converter"() {
        given:
        AccessTypeDocRepository repository = context.getBean(AccessTypeDocRepository)
        FloatVector embedding = (FloatVector) Vector.of([1f, 2f, 3f] as float[])

        when:
        AccessTypeDoc saved = repository.save(new AccessTypeDoc(embedding: embedding))
        AccessTypeDoc read = repository.findById(saved.id).orElseThrow()

        then:
        read.embedding == embedding
        context.getBean(R2dbcOnlyVectorConverter).calls == 0
    }

    /**
     * Registered first: it fails the test if a JDBC operation picks it.
     */
    @Singleton
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Requires(property = 'spec.name', value = 'OracleJdbcVectorDataAccessTypeSpec')
    static class R2dbcOnlyVectorConverter implements VectorTypeConverter<VECTOR> {

        int calls

        @Override
        VECTOR convert(Vector vector) {
            calls++
            throw new IllegalStateException("The R2DBC converter must not be used by JDBC operations")
        }

        @Override
        Vector convert(VECTOR object, Class<Vector> targetType) {
            calls++
            throw new IllegalStateException("The R2DBC converter must not be used by JDBC operations")
        }

        @Override
        Set<Class<? extends Vector>> supportedVectorTypes() {
            return [Vector, FloatVector] as Set
        }

        @Override
        DatabaseType databaseType() {
            return DatabaseType.ORACLE
        }

        @Override
        Class<VECTOR> getPersistedType() {
            return VECTOR
        }

        @Override
        DataAccessType dataAccessType() {
            return DataAccessType.R2DBC
        }
    }
}

@MappedEntity("vector_access_type_doc")
class AccessTypeDoc {
    @Id
    @GeneratedValue
    Long id

    @VectorStorage(length = 3)
    FloatVector embedding
}

@Requires(property = 'spec.name', value = 'OracleJdbcVectorDataAccessTypeSpec')
@JdbcRepository(dialect = Dialect.ORACLE)
interface AccessTypeDocRepository extends CrudRepository<AccessTypeDoc, Long> {
}
