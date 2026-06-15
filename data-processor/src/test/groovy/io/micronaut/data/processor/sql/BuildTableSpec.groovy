/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.sql

import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.data.tck.jdbc.entities.Employee
import io.micronaut.data.tck.jdbc.entities.EmployeeGroup
import spock.lang.Unroll

class BuildTableSpec extends AbstractDataSpec {


    void "test build create table table statement for nullable embeddable"() {
        given:
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ANSI)
        def entity = PersistentEntity.of(Restaurant)
        def sql = builder.buildBatchCreateTableStatement(List.of(), entity)

        expect:"@Nullable @Embedded doesn't include NOT NULL declaration"
        sql.contains("\"hqaddress_street\" VARCHAR(255),")

        and:"regular @Embedded does include NOT NULL declaration"
        sql.contains("\"street\" VARCHAR(255) NOT NULL,")
    }

    void "shared identity join columns do not duplicate embedded id columns in ddl"() {
        given:
        def entity = buildEntity('test.Asset', '''
import java.util.UUID;
import jakarta.persistence.JoinColumn;

@MappedEntity("asset")
class Asset {
    @EmbeddedId
    private AssetId id;

    private String title;

    @Relation(value = Relation.Kind.ONE_TO_ONE, cascade = Relation.Cascade.NONE)
    @JoinColumn(name = "container_id", referencedColumnName = "container_id")
    @JoinColumn(name = "asset_id", referencedColumnName = "asset_id")
    private AssetMetadata metadata;

    AssetId getId() {
        return id;
    }

    void setId(AssetId id) {
        this.id = id;
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    AssetMetadata getMetadata() {
        return metadata;
    }

    void setMetadata(AssetMetadata metadata) {
        this.metadata = metadata;
    }
}

@Embeddable
class AssetId {
    @MappedProperty("container_id")
    private UUID containerId;

    @MappedProperty("asset_id")
    private Integer assetId;

    UUID getContainerId() {
        return containerId;
    }

    void setContainerId(UUID containerId) {
        this.containerId = containerId;
    }

    Integer getAssetId() {
        return assetId;
    }

    void setAssetId(Integer assetId) {
        this.assetId = assetId;
    }
}

@MappedEntity("assetmetadata")
class AssetMetadata {
    @EmbeddedId
    private AssetId id;

    private String author;

    AssetId getId() {
        return id;
    }

    void setId(AssetId id) {
        this.id = id;
    }

    String getAuthor() {
        return author;
    }

    void setAuthor(String author) {
        this.author = author;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.H2)
        def sql = builder.buildBatchCreateTableStatement(List.of(), entity)

        expect:
        !sql.contains("metadata")
        sql.count("`container_id`") == 2
        sql.count("`asset_id`") == 2
    }

    void "duplicate physical identity columns fail ddl mapping"() {
        given:
        def entity = buildEntity('test.ConflictingIdentityEntity', '''
@MappedEntity("conflicting_identity_entity")
class ConflictingIdentityEntity {
    @EmbeddedId
    private ConflictingIdentity id;

    ConflictingIdentity getId() {
        return id;
    }

    void setId(ConflictingIdentity id) {
        this.id = id;
    }
}

@Embeddable
class ConflictingIdentity {
    @MappedProperty("shared_id")
    private Long firstId;

    @MappedProperty("shared_id")
    private Long secondId;

    Long getFirstId() {
        return firstId;
    }

    void setFirstId(Long firstId) {
        this.firstId = firstId;
    }

    Long getSecondId() {
        return secondId;
    }

    void setSecondId(Long secondId) {
        this.secondId = secondId;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.H2)

        when:
        builder.buildBatchCreateTableStatement(List.of(), entity)

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Conflicting identity mapping for column [shared_id]")
    }

    void "plain embedded property mapped to identity column fails ddl mapping"() {
        given:
        def entity = buildEntity('test.ConflictingEmbeddedEntity', '''
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

@MappedEntity("conflicting_embedded_entity")
class ConflictingEmbeddedEntity {
    @Id
    private Long id;

    @Relation(Relation.Kind.EMBEDDED)
    private ConflictingEmbeddedValue details;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    ConflictingEmbeddedValue getDetails() {
        return details;
    }

    void setDetails(ConflictingEmbeddedValue details) {
        this.details = details;
    }
}

@Embeddable
class ConflictingEmbeddedValue {
    @MappedProperty("id")
    private Long id;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.H2)

        when:
        builder.buildBatchCreateTableStatement(List.of(), entity)

        then:
        def ex = thrown(RuntimeException)
        ex.message.contains("Conflicting table mapping for column [id]")
    }

    @Unroll
    void "test build create table for JSON type for dialect #dialect"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Version;
import java.util.Map;

@Entity
class Test {

    @javax.persistence.Id
    @GeneratedValue
    private Long id;

    @io.micronaut.data.annotation.TypeDef(type=io.micronaut.data.model.DataType.JSON)
    private Map json;

    @Version
    @GeneratedValue
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Map getJson() {
        return json;
    }

    public void setJson(Map json) {
        this.json = json;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def sql = builder.buildBatchCreateTableStatement(List.of(), entity)

        expect:
        sql == statement

        where:
        dialect          | statement
        Dialect.H2       | 'CREATE TABLE `test` (`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,`json` JSON NOT NULL);'
        Dialect.MYSQL    | 'CREATE TABLE `test` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`json` JSON NOT NULL);'
        Dialect.POSTGRES | 'CREATE TABLE "test" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"json" JSONB NOT NULL);'
        Dialect.ORACLE   | 'CREATE TABLE "TEST" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"JSON" JSON NOT NULL)' + System.lineSeparator() +
                           'CREATE SEQUENCE "TEST_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE'
    }

    void "test custom column definition"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import java.time.LocalDateTime;

@Entity
class Test {

    @javax.persistence.Id
    @GeneratedValue
    private Long id;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    @DateCreated
    private LocalDateTime dateCreated;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDateTime ldt) {
        dateCreated = ldt;
    }
}
''')

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.MYSQL)
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE `test` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`date_created` TIMESTAMP WITH TIME ZONE);'
    }

    void "test custom parent entity with generics"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import java.time.LocalDateTime;

@Entity
class Test extends io.micronaut.data.tck.entities.BaseEntity<Long> {
}
''')

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.MYSQL)
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE `test` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`created_date` TIMESTAMP(6) DEFAULT NOW(6),`updated_date` TIMESTAMP(6) DEFAULT NOW(6));'
    }

    @Unroll
    void "test custom column restrictions #dialect"() {
        given:
            def entity = buildJpaEntity('test.Test', '''
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
class Test {

    @javax.persistence.Id
    @GeneratedValue
    private Long id;

    @Column
    private String text1;
    @Column(length=10)
    private String text2;
    @jakarta.validation.constraints.Size(min=5, max=7)
    private String text3;

    private BigDecimal amount1;
    @Column(precision = 11, scale = 2)
    private BigDecimal amount2;
    @jakarta.validation.constraints.Size(min=5, max=7)
    private BigDecimal amount3;

    private Float floatAmount1;
    @Column(precision = 11, scale = 2)
    private Float floatAmount2;

    private Double doubleAmount1;
    @Column(precision = 11, scale = 2)
    private Double doubleAmount2;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getText1() {
        return text1;
    }

    public void setText1(String text1) {
        this.text1 = text1;
    }

    public String getText2() {
        return text2;
    }

    public void setText2(String text2) {
        this.text2 = text2;
    }

    public String getText3() {
        return text3;
    }

    public void setText3(String text3) {
        this.text3 = text3;
    }

    public BigDecimal getAmount1() {
        return amount1;
    }

    public void setAmount1(BigDecimal amount1) {
        this.amount1 = amount1;
    }

    public BigDecimal getAmount2() {
        return amount2;
    }

    public void setAmount2(BigDecimal amount2) {
        this.amount2 = amount2;
    }

    public BigDecimal getAmount3() {
        return amount3;
    }

    public void setAmount3(BigDecimal amount3) {
        this.amount3 = amount3;
    }

    public Float getFloatAmount1() {
        return floatAmount1;
    }

    public void setFloatAmount1(Float floatAmount1) {
        this.floatAmount1 = floatAmount1;
    }

    public Float getFloatAmount2() {
        return floatAmount2;
    }

    public void setFloatAmount2(Float floatAmount2) {
        this.floatAmount2 = floatAmount2;
    }

    public Double getDoubleAmount1() {
        return doubleAmount1;
    }

    public void setDoubleAmount1(Double doubleAmount1) {
        this.doubleAmount1 = doubleAmount1;
    }

    public Double getDoubleAmount2() {
        return doubleAmount2;
    }

    public void setDoubleAmount2(Double doubleAmount2) {
        this.doubleAmount2 = doubleAmount2;
    }
}
''')

        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def sql = builder.buildBatchCreateTableStatement(List.of(), entity)

        expect:
        sql == statement

        where:
        dialect          | statement
        Dialect.H2       | 'CREATE TABLE `test` (`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,`text1` VARCHAR(255) NOT NULL,`text2` VARCHAR(10) NOT NULL,`text3` VARCHAR(7) NOT NULL,`amount1` DECIMAL NOT NULL,`amount2` NUMERIC(11,2) NOT NULL,`amount3` DECIMAL NOT NULL,`float_amount1` FLOAT NOT NULL,`float_amount2` NUMERIC(11,2) NOT NULL,`double_amount1` DOUBLE NOT NULL,`double_amount2` NUMERIC(11,2) NOT NULL);'
        Dialect.MYSQL    | 'CREATE TABLE `test` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`text1` VARCHAR(255) NOT NULL,`text2` VARCHAR(10) NOT NULL,`text3` VARCHAR(7) NOT NULL,`amount1` DECIMAL NOT NULL,`amount2` NUMERIC(11,2) NOT NULL,`amount3` DECIMAL NOT NULL,`float_amount1` FLOAT NOT NULL,`float_amount2` NUMERIC(11,2) NOT NULL,`double_amount1` DOUBLE NOT NULL,`double_amount2` NUMERIC(11,2) NOT NULL);'
        Dialect.POSTGRES | 'CREATE TABLE "test" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"text1" VARCHAR(255) NOT NULL,"text2" VARCHAR(10) NOT NULL,"text3" VARCHAR(7) NOT NULL,"amount1" DECIMAL NOT NULL,"amount2" NUMERIC(11,2) NOT NULL,"amount3" DECIMAL NOT NULL,"float_amount1" REAL NOT NULL,"float_amount2" NUMERIC(11,2) NOT NULL,"double_amount1" DOUBLE PRECISION NOT NULL,"double_amount2" NUMERIC(11,2) NOT NULL);'
        Dialect.ORACLE   | 'CREATE TABLE "TEST" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"TEXT1" VARCHAR(255) NOT NULL,"TEXT2" VARCHAR(10) NOT NULL,"TEXT3" VARCHAR(7) NOT NULL,"AMOUNT1" FLOAT(126) NOT NULL,"AMOUNT2" NUMBER(11,2) NOT NULL,"AMOUNT3" FLOAT(126) NOT NULL,"FLOAT_AMOUNT1" FLOAT(53) NOT NULL,"FLOAT_AMOUNT2" NUMBER(11,2) NOT NULL,"DOUBLE_AMOUNT1" FLOAT(23) NOT NULL,"DOUBLE_AMOUNT2" NUMBER(11,2) NOT NULL)' + System.lineSeparator() +
                           'CREATE SEQUENCE "TEST_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE'
    }

    @Unroll
    void "test time datatype #dialect"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import io.micronaut.data.annotation.MappedProperty;
import java.sql.Time;

@Entity
class Test {
    @MappedProperty("wakeup_time")
    private Time wakeUpTime;

    public void setWakeUpTime(Time wakeUpTime) {
        this.wakeUpTime = wakeUpTime;
    }

    public Time getWakeUpTime() {
        return wakeUpTime;
    }}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def sql = builder.buildBatchCreateTableStatement(List.of(), entity)

        expect:
        sql == statement

        where:
        dialect          | statement
        Dialect.H2       | 'CREATE TABLE `test` (`wakeup_time` TIME(6) NOT NULL);'
        Dialect.MYSQL    | 'CREATE TABLE `test` (`wakeup_time` TIME(6) NOT NULL);'
        Dialect.POSTGRES | 'CREATE TABLE "test" ("wakeup_time" TIME(6) NOT NULL);'
        Dialect.ORACLE   | 'CREATE TABLE "TEST" ("WAKEUP_TIME" DATE  NOT NULL)'
    }

    void "test create table MappedProperty with Embedded"() {
        given:
        def entity = buildJpaEntity('test.EmbeddedEntity', '''
import io.micronaut.data.annotation.Embeddable;import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;

@MappedEntity
class EmbeddedEntity {
    @Id
    private Long id;

    @MappedProperty("emb_a_")
    @Relation(Relation.Kind.EMBEDDED)
    private Emb embA;

    @MappedProperty("emb_b_")
    @Relation(Relation.Kind.EMBEDDED)
    private Emb embB;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Emb getEmbA() {
        return this.embA;
    }

    public void setEmbA(Emb embA) {
        this.embA = embA;
    }

    public Emb getEmbB() {
        return this.embB;
    }

    public void setEmbB(Emb embB) {
        this.embB = embB;
    }
}
@Embeddable
class Emb {
    private String a;
    private String b;

    public String getA() {
        return  a;
    }
    public void setA(String a) {
        this.a = a;
    }
    public String getB() {
        return  b;
    }
    public void setB(String b) {
        this.b = b;
    }
}
''')

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildBatchCreateTableStatement(List.of(), entity)

        then:
        sql == 'CREATE TABLE "embedded_entity" ("id" BIGINT NOT NULL,"emb_a_a" VARCHAR(255) NOT NULL,"emb_a_b" VARCHAR(255) NOT NULL,"emb_b_a" VARCHAR(255) NOT NULL,"emb_b_b" VARCHAR(255) NOT NULL, PRIMARY KEY("id"));'
    }

    void "test create table OneToMany with JoinColumn"() {
        given:
        def employeeEntity = PersistentEntity.of(Employee)
        def employeeGroupEntity = PersistentEntity.of(EmployeeGroup)
        def builder = new SqlQueryBuilder(Dialect.H2)

        when:"Tables are created"
        def employeeSql = builder.buildCreateTableStatements(employeeEntity)
        def employeeGroupSql = builder.buildCreateTableStatements(employeeGroupEntity)
        then:"No join table is created"
        employeeSql.length == 1
        employeeSql[0] == 'CREATE TABLE `employee` (`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,`name` VARCHAR(255) NOT NULL,`category_id` BIGINT NOT NULL,`employer_id` BIGINT NOT NULL);'
        employeeGroupSql.length == 1
        employeeGroupSql[0] == 'CREATE TABLE `employee_group` (`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,`name` VARCHAR(255) NOT NULL,`category_id` BIGINT NOT NULL,`employer_id` BIGINT NOT NULL);'
    }

    void "test create ManyToMany table with schema"() {
        given:
        def entity = buildJpaEntity('test.Student', '''
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinTable;

@MappedEntity(value = "m2m_student", schema = "students")
class Student {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @JoinTable(
            name = "m2m_student_course_association",
            joinColumns = @JoinColumn(name = "st_id"),
            inverseJoinColumns = @JoinColumn(name = "cs_id"),
            schema = "students")
    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    private List<Course> courses;
    @JoinTable(
            name = "m2m_student_teacher_association",
            joinColumns = @JoinColumn(name = "st_id"),
            inverseJoinColumns = @JoinColumn(name = "te_id"))
    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    private List<Teacher> teachers;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Course> getCourses() { return courses; }
    public void setCourses(List<Course> courses) { this.courses = courses; }
    public List<Teacher> getTeachers() { return teachers; }
    public void setTeachers(List<Teacher> teachers) { this.teachers = teachers; }
}

@MappedEntity(value = "m2m_course", schema = "students")
class Course {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
    private List<Student> students;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Student> getStudents() { return students; }
    public void setStudents(List<Student> students) { this.students = students; }
}

@MappedEntity(value = "m2m_teacher", schema = "students")
class Teacher {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "teachers")
    private List<Student> students;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Student> getStudents() { return students; }
    public void setStudents(List<Student> students) { this.students = students; }
}

''')

        when:
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.MYSQL)
        def sql = builder.buildCreateTableStatements(entity)

        then:
        sql.length == 4
        sql[0] == 'CREATE SCHEMA `students`;'
        sql[1] == 'CREATE TABLE `students`.`m2m_student_course_association` (`st_id` BIGINT NOT NULL,`cs_id` BIGINT NOT NULL, PRIMARY KEY(`st_id`,`cs_id`));'
        sql[2] == 'CREATE TABLE `students`.`m2m_student_teacher_association` (`st_id` BIGINT NOT NULL,`te_id` BIGINT NOT NULL, PRIMARY KEY(`st_id`,`te_id`));'
        sql[3] == 'CREATE TABLE `students`.`m2m_student` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`name` VARCHAR(255) NOT NULL);'
    }

    @Unroll
    void "test build create table for Duration and Period types for dialect #dialect"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import java.time.Duration;
import java.time.Period;

@MappedEntity
class Test {

    @Id
    @GeneratedValue
    private Long id;

    private Duration duration;

    private Period period;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public Period getPeriod() {
        return period;
    }

    public void setPeriod(Period period) {
        this.period = period;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:
        sql == statement

        where:
        dialect          | statement
        Dialect.H2       | 'CREATE TABLE `test` (`id` BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,`duration` VARCHAR(255) NOT NULL,`period` VARCHAR(255) NOT NULL);'
        Dialect.MYSQL    | 'CREATE TABLE `test` (`id` BIGINT PRIMARY KEY AUTO_INCREMENT,`duration` VARCHAR(255) NOT NULL,`period` VARCHAR(255) NOT NULL);'
        Dialect.POSTGRES | 'CREATE TABLE "test" ("id" BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,"duration" VARCHAR(255) NOT NULL,"period" VARCHAR(255) NOT NULL);'
        Dialect.ORACLE   | 'CREATE TABLE "TEST" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"DURATION" INTERVAL DAY TO SECOND NOT NULL,"PERIOD" INTERVAL YEAR TO MONTH NOT NULL)' + System.lineSeparator() +
                           'CREATE SEQUENCE "TEST_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE'
    }
}
