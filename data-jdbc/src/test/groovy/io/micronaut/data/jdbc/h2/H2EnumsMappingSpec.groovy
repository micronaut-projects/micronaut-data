package io.micronaut.data.jdbc.h2

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.DataType
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated

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
interface EnumEntityRepository extends CrudRepository<EnumEntity, Long> {

    int update(@Id Long id, MyEnum asDefault, MyEnum asString, MyEnum asInt)

    Optional<EnumEntity> find(MyEnum asDefault, MyEnum asString, MyEnum asInt)

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
}