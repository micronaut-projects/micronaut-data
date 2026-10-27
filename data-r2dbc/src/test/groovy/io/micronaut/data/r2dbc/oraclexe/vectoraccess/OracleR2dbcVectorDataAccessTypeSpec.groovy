package io.micronaut.data.r2dbc.oraclexe.vectoraccess

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.order.Ordered
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.convert.DataAccessType
import io.micronaut.data.model.runtime.convert.DatabaseType
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConverter
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.oraclexe.OracleXETestPropertyProvider
import io.micronaut.data.repository.CrudRepository
import jakarta.inject.Singleton
import oracle.sql.VECTOR
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The write and read paths of R2DBC repositories must ignore vector converters of another driver API, such as
 * the JDBC ones when both modules are on the classpath.
 */
class OracleR2dbcVectorDataAccessTypeSpec extends Specification implements OracleXETestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ['spec.name': 'OracleR2dbcVectorDataAccessTypeSpec'])

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void "an R2DBC save and read never use a JDBC vector converter"() {
        given:
        R2dbcAccessTypeDocRepository repository = context.getBean(R2dbcAccessTypeDocRepository)
        FloatVector embedding = (FloatVector) Vector.of([1f, 2f, 3f] as float[])

        when:
        R2dbcAccessTypeDoc saved = repository.save(new R2dbcAccessTypeDoc(embedding: embedding))
        R2dbcAccessTypeDoc read = repository.findById(saved.id).orElseThrow()

        then:
        read.embedding == embedding
        context.getBean(JdbcOnlyVectorConverter).calls == 0
    }

    /**
     * Registered first: it fails the test if an R2DBC operation picks it.
     */
    @Singleton
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Requires(property = 'spec.name', value = 'OracleR2dbcVectorDataAccessTypeSpec')
    static class JdbcOnlyVectorConverter implements VectorTypeConverter<VECTOR> {

        int calls

        @Override
        VECTOR convert(Vector vector) {
            calls++
            throw new IllegalStateException("The JDBC converter must not be used by R2DBC operations")
        }

        @Override
        Vector convert(VECTOR object, Class<Vector> targetType) {
            calls++
            throw new IllegalStateException("The JDBC converter must not be used by R2DBC operations")
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
            return DataAccessType.JDBC
        }
    }
}

@MappedEntity("r2dbc_vector_access_type_doc")
class R2dbcAccessTypeDoc {
    @Id
    @GeneratedValue
    Long id

    @VectorStorage(length = 3)
    FloatVector embedding
}

@Requires(property = 'spec.name', value = 'OracleR2dbcVectorDataAccessTypeSpec')
@R2dbcRepository(dialect = Dialect.ORACLE)
interface R2dbcAccessTypeDocRepository extends CrudRepository<R2dbcAccessTypeDoc, Long> {
}
