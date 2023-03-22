package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.DataType
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import javax.transaction.Transactional

@MicronautTest
class H2EnumsMappingSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    EnumEntityRepository enumEntityRepository = applicationContext.getBean(EnumEntityRepository)

    @Shared
    @Inject
    JpaEnumEntityRepository jpaEnumEntityRepository = applicationContext.getBean(JpaEnumEntityRepository)

    void 'test inserts are broken for custom queries'() {
        expect:
        enumEntityRepository.insertValue("b", "b", 1)
    }

    void 'test read lower case enum'() {
        when:
        enumEntityRepository.insertValueExplicit("B", "b")
        def result = enumEntityRepository.findByAsString(MyEnum.B)

        then:
        result != null
        result.asDefault == MyEnum.B
        result.asString == MyEnum.B
    }

    void 'test enums'() {
        given:
            EnumEntity entity = new EnumEntity()
            entity.asDefault = MyEnum.A
            entity.asString = MyEnum.B
            entity.asInt = MyEnum.C
        when:
            enumEntityRepository.save(entity)
            entity = enumEntityRepository.findById(entity.id).get()
        then:
            entity.asDefault == MyEnum.A
            entity.asString == MyEnum.B
            entity.asInt == MyEnum.C
        when:
            def dto = enumEntityRepository.queryById(entity.id)
        then:
            dto.asDefault == "a"
            dto.asString == "b"
            dto.asInt == 2
        when:
            int updated = enumEntityRepository.update(entity.id, MyEnum.D, MyEnum.E, MyEnum.F)
            entity = enumEntityRepository.findById(entity.id).get()
        then:
            updated == 1
            entity.asDefault == MyEnum.D
            entity.asString == MyEnum.E
            entity.asInt == MyEnum.F
        when:
            def result = enumEntityRepository.find(MyEnum.D, MyEnum.E, MyEnum.F)
        then:
            result.get().id == entity.id
    }

    void 'jpa test enums'() {
        given:
            JpaEnumEntity entity = new JpaEnumEntity()
            entity.asDefault = MyEnum.A
            entity.asString = MyEnum.B
            entity.asInt = MyEnum.C
        when:
            jpaEnumEntityRepository.save(entity)
            entity = jpaEnumEntityRepository.findById(entity.id).get()
        then:
            entity.asDefault == MyEnum.A
            entity.asString == MyEnum.B
            entity.asInt == MyEnum.C
        when:
            int updated = jpaEnumEntityRepository.update(entity.id, MyEnum.D, MyEnum.E, MyEnum.F)
            entity = jpaEnumEntityRepository.findById(entity.id).get()
        then:
            updated == 1
            entity.asDefault == MyEnum.D
            entity.asString == MyEnum.E
            entity.asInt == MyEnum.F
        when:
            def result = jpaEnumEntityRepository.find(MyEnum.D, MyEnum.E, MyEnum.F)
        then:
            result.get().id == entity.id
    }

    void "test create table with enums"() {
        given:
            SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.H2)

        when:
            def sql = builder.buildBatchCreateTableStatement(PersistentEntity.of(EnumEntity))

        then:
            sql == 'CREATE TABLE `enum_entity` (`id` BIGINT AUTO_INCREMENT PRIMARY KEY,`as_default` VARCHAR(255) NOT NULL,`as_string` VARCHAR(255) NOT NULL,`as_int` INT NOT NULL);'
    }

    void "test jpa create table with enums"() {
        given:
            SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.H2)

        when:
            def sql = builder.buildBatchCreateTableStatement(PersistentEntity.of(JpaEnumEntity))

        then:
            sql == 'CREATE TABLE `jpa_enum_entity` (`id` BIGINT AUTO_INCREMENT PRIMARY KEY,`as_default` INT NOT NULL,`as_string` VARCHAR(255) NOT NULL,`as_int` INT NOT NULL);'
    }

}

@JdbcRepository(dialect = Dialect.H2)
abstract class EnumEntityRepository implements CrudRepository<EnumEntity, Long> {
    private final JdbcOperations jdbcOperations;

    EnumEntityRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations
    }

    @Transactional
    void insertValueExplicit(String a, b) {
        jdbcOperations.prepareStatement("INSERT INTO ENUM_ENTITY(as_default, as_string, as_int) VALUES(?,?,1)", {
            it.setString(1, a)
            it.setString(2, b)
            it.execute()
        })
    }

    @Query("INSERT INTO ENUM_ENTITY(as_default, as_string, as_int) VALUES(:asDefault,:asString,:asInt)")
    abstract void insertValue(String asDefault, String asString, int asInt)

    abstract EnumEntity findByAsString(MyEnum value)

    abstract int update(@Id Long id, MyEnum asDefault, MyEnum asString, MyEnum asInt)

    abstract Optional<EnumEntity> find(MyEnum asDefault, MyEnum asString, MyEnum asInt)

    abstract EnumEntityDto queryById(Long id)

}

@JdbcRepository(dialect = Dialect.H2)
interface JpaEnumEntityRepository extends CrudRepository<JpaEnumEntity, Long> {

    int update(@Id Long id, MyEnum asDefault, MyEnum asString, MyEnum asInt)

    Optional<JpaEnumEntity> find(MyEnum asDefault, MyEnum asString, MyEnum asInt)

}

@MappedEntity
class EnumEntity {

    @Id
    @GeneratedValue
    Long id
    MyEnum asDefault
    @TypeDef(type = DataType.STRING)
    MyEnum asString
    @TypeDef(type = DataType.INTEGER)
    MyEnum asInt
}

@Introspected
class EnumEntityDto {

    String asDefault
    String asString
    Object asInt
}

@Entity
class JpaEnumEntity {
    @javax.persistence.Id
    @javax.persistence.GeneratedValue
    Long id
    MyEnum asDefault
    @Enumerated(EnumType.STRING)
    MyEnum asString
    @Enumerated(EnumType.ORDINAL)
    MyEnum asInt
}

enum MyEnum {
    A, B, C, D, E, F

    @Override
    String toString() {
        return name().toLowerCase();
    }
}
