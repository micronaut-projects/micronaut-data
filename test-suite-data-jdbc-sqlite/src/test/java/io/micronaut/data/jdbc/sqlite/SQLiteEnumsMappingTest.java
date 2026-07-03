package io.micronaut.data.jdbc.sqlite;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite")
class SQLiteEnumsMappingTest {

    @Inject
    EnumEntityRepository enumEntityRepository;

    @Inject
    JpaEnumEntityRepository jpaEnumEntityRepository;

    @AfterEach
    void cleanup() {
        enumEntityRepository.deleteAll();
        jpaEnumEntityRepository.deleteAll();
    }

    @Test
    void testInsertsAreBrokenForCustomQueries() {
        assertDoesNotThrow(() -> enumEntityRepository.insertValue("b", "b", 1));
    }

    @Test
    void testReadLowerCaseEnum() {
        enumEntityRepository.insertValueExplicit("B", "b");
        EnumEntity result = enumEntityRepository.findByAsString(MyEnum.B);

        assertNotNull(result);
        assertEquals(MyEnum.B, result.getAsDefault());
        assertEquals(MyEnum.B, result.getAsString());
    }

    @Test
    void testEnums() {
        EnumEntity entity = new EnumEntity();
        entity.setAsDefault(MyEnum.A);
        entity.setAsString(MyEnum.B);
        entity.setAsInt(MyEnum.C);

        entity = enumEntityRepository.save(entity);
        entity = enumEntityRepository.findById(entity.getId()).orElseThrow();

        assertEquals(MyEnum.A, entity.getAsDefault());
        assertEquals(MyEnum.B, entity.getAsString());
        assertEquals(MyEnum.C, entity.getAsInt());

        EnumEntityDto dto = enumEntityRepository.queryById(entity.getId());

        assertEquals("a", dto.getAsDefault());
        assertEquals("b", dto.getAsString());
        assertEquals(2, dto.getAsInt());

        int updated = enumEntityRepository.update(entity.getId(), MyEnum.D, MyEnum.E, MyEnum.F);
        entity = enumEntityRepository.findById(entity.getId()).orElseThrow();

        assertEquals(1, updated);
        assertEquals(MyEnum.D, entity.getAsDefault());
        assertEquals(MyEnum.E, entity.getAsString());
        assertEquals(MyEnum.F, entity.getAsInt());

        Optional<EnumEntity> result = enumEntityRepository.find(MyEnum.D, MyEnum.E, MyEnum.F);
        assertEquals(entity.getId(), result.orElseThrow().getId());
    }

    @Test
    void jpaTestEnums() {
        JpaEnumEntity entity = new JpaEnumEntity();
        entity.setAsDefault(MyEnum.A);
        entity.setAsString(MyEnum.B);
        entity.setAsInt(MyEnum.C);

        entity = jpaEnumEntityRepository.save(entity);
        entity = jpaEnumEntityRepository.findById(entity.getId()).orElseThrow();

        assertEquals(MyEnum.A, entity.getAsDefault());
        assertEquals(MyEnum.B, entity.getAsString());
        assertEquals(MyEnum.C, entity.getAsInt());

        int updated = jpaEnumEntityRepository.update(entity.getId(), MyEnum.D, MyEnum.E, MyEnum.F);
        entity = jpaEnumEntityRepository.findById(entity.getId()).orElseThrow();

        assertEquals(1, updated);
        assertEquals(MyEnum.D, entity.getAsDefault());
        assertEquals(MyEnum.E, entity.getAsString());
        assertEquals(MyEnum.F, entity.getAsInt());

        Optional<JpaEnumEntity> result = jpaEnumEntityRepository.find(MyEnum.D, MyEnum.E, MyEnum.F);
        assertEquals(entity.getId(), result.orElseThrow().getId());
    }

    @Test
    void testCreateTableWithEnums() {
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.SQLITE);

        String sql = builder.buildBatchCreateTableStatement(PersistentEntity.of(EnumEntity.class));

        assertEquals("CREATE TABLE \"enum_entity\" (\"id\" INTEGER PRIMARY KEY,\"as_default\" VARCHAR(255) NOT NULL,\"as_string\" VARCHAR(255) NOT NULL,\"as_int\" INT NOT NULL);", sql);
    }

    @Test
    void testJpaCreateTableWithEnums() {
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.SQLITE);

        String sql = builder.buildBatchCreateTableStatement(PersistentEntity.of(JpaEnumEntity.class));

        assertEquals("CREATE TABLE \"jpa_enum_entity\" (\"id\" INTEGER PRIMARY KEY,\"as_default\" INT NOT NULL,\"as_string\" VARCHAR(255) NOT NULL,\"as_int\" INT NOT NULL);", sql);
    }

    @Test
    void testCreateTableWithEnums2() {
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.SQLITE);

        String sql = builder.buildBatchCreateTableStatement(PersistentEntity.of(EnumEntity.class));

        assertEquals("CREATE TABLE \"enum_entity\" (\"id\" INTEGER PRIMARY KEY,\"as_default\" VARCHAR(255) NOT NULL,\"as_string\" VARCHAR(255) NOT NULL,\"as_int\" INT NOT NULL);", sql);
    }

    @Test
    void testJpaCreateTableWithEnums2() {
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.SQLITE);

        String sql = builder.buildBatchCreateTableStatement(PersistentEntity.of(JpaEnumEntity.class));

        assertEquals("CREATE TABLE \"jpa_enum_entity\" (\"id\" INTEGER PRIMARY KEY,\"as_default\" INT NOT NULL,\"as_string\" VARCHAR(255) NOT NULL,\"as_int\" INT NOT NULL);", sql);
    }
}

@JdbcRepository(dialect = Dialect.SQLITE)
abstract class EnumEntityRepository implements CrudRepository<EnumEntity, Long> {

    private final JdbcOperations jdbcOperations;

    EnumEntityRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @Transactional
    void insertValueExplicit(String a, String b) {
        jdbcOperations.prepareStatement("INSERT INTO ENUM_ENTITY(as_default, as_string, as_int) VALUES(?,?,1)", statement -> {
            statement.setString(1, a);
            statement.setString(2, b);
            return statement.execute();
        });
    }

    @Query("INSERT INTO ENUM_ENTITY(as_default, as_string, as_int) VALUES(:asDefault,:asString,:asInt)")
    abstract void insertValue(String asDefault, String asString, int asInt);

    abstract EnumEntity findByAsString(MyEnum value);

    abstract int update(@Id Long id, MyEnum asDefault, MyEnum asString, MyEnum asInt);

    abstract Optional<EnumEntity> find(MyEnum asDefault, MyEnum asString, MyEnum asInt);

    abstract EnumEntityDto queryById(Long id);
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface JpaEnumEntityRepository extends CrudRepository<JpaEnumEntity, Long> {

    int update(@Id Long id, MyEnum asDefault, MyEnum asString, MyEnum asInt);

    Optional<JpaEnumEntity> find(MyEnum asDefault, MyEnum asString, MyEnum asInt);
}

@MappedEntity
class EnumEntity {

    @Id
    @GeneratedValue
    private Long id;
    private MyEnum asDefault;

    @TypeDef(type = DataType.STRING)
    private MyEnum asString;

    @TypeDef(type = DataType.INTEGER)
    private MyEnum asInt;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    MyEnum getAsDefault() {
        return asDefault;
    }

    void setAsDefault(MyEnum asDefault) {
        this.asDefault = asDefault;
    }

    MyEnum getAsString() {
        return asString;
    }

    void setAsString(MyEnum asString) {
        this.asString = asString;
    }

    MyEnum getAsInt() {
        return asInt;
    }

    void setAsInt(MyEnum asInt) {
        this.asInt = asInt;
    }
}

@Introspected
class EnumEntityDto {

    private String asDefault;
    private String asString;
    private Object asInt;

    String getAsDefault() {
        return asDefault;
    }

    void setAsDefault(String asDefault) {
        this.asDefault = asDefault;
    }

    String getAsString() {
        return asString;
    }

    void setAsString(String asString) {
        this.asString = asString;
    }

    Object getAsInt() {
        return asInt;
    }

    void setAsInt(Object asInt) {
        this.asInt = asInt;
    }
}

@Entity
class JpaEnumEntity {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue
    private Long id;
    private MyEnum asDefault;

    @Enumerated(EnumType.STRING)
    private MyEnum asString;

    @Enumerated(EnumType.ORDINAL)
    private MyEnum asInt;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    MyEnum getAsDefault() {
        return asDefault;
    }

    void setAsDefault(MyEnum asDefault) {
        this.asDefault = asDefault;
    }

    MyEnum getAsString() {
        return asString;
    }

    void setAsString(MyEnum asString) {
        this.asString = asString;
    }

    MyEnum getAsInt() {
        return asInt;
    }

    void setAsInt(MyEnum asInt) {
        this.asInt = asInt;
    }
}

enum MyEnum {
    A, B, C, D, E, F;

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
