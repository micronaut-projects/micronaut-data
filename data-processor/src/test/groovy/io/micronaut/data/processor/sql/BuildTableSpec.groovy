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
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Restaurant
import io.micronaut.data.tck.jdbc.entities.Employee
import io.micronaut.data.tck.jdbc.entities.EmployeeGroup
import spock.lang.Unroll

class BuildTableSpec extends AbstractDataSpec {

    void "test build create table with Oracle reservable column"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Reservable;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
class Account {
    @javax.persistence.Id
    private Long id;
    private String name;
    @Reservable
    @PositiveOrZero
    private Long balance;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def sql = builder.buildCreateTableStatements(entity).join(System.lineSeparator())

        then:
        sql == 'CREATE TABLE "ACCOUNT" ("ID" NUMBER(19) NOT NULL,"NAME" VARCHAR(255) NOT NULL,"BALANCE" NUMBER(19) RESERVABLE CONSTRAINT "CK_ACCOUNT_BALANCE_GE_0" CHECK ("BALANCE" >= 0) NOT NULL, PRIMARY KEY("ID"))'
    }

    void "test Oracle reservable constraint names use the mapped table name"() {
        given:
        def first = buildJpaEntity('test.FirstAccount', '''
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Reservable;
import jakarta.validation.constraints.PositiveOrZero;

@MappedEntity("ledger_a")
class FirstAccount {
    @javax.persistence.Id private Long id;
    @Reservable @PositiveOrZero private Long balance;
    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    Long getBalance() { return balance; }
    void setBalance(Long balance) { this.balance = balance; }
}
''')
        def second = buildJpaEntity('test.SecondAccount', '''
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Reservable;
import jakarta.validation.constraints.PositiveOrZero;

@MappedEntity("ledger_b")
class SecondAccount {
    @javax.persistence.Id private Long id;
    @Reservable @PositiveOrZero private Long balance;
    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    Long getBalance() { return balance; }
    void setBalance(Long balance) { this.balance = balance; }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def firstSql = builder.buildCreateTableStatements(first).join(System.lineSeparator())
        def secondSql = builder.buildCreateTableStatements(second).join(System.lineSeparator())

        then:
        firstSql.contains('CONSTRAINT "CK_LEDGER_A_BALANCE_GE_0"')
        secondSql.contains('CONSTRAINT "CK_LEDGER_B_BALANCE_GE_0"')
    }

    void "test build create table with Oracle reservable column without validation constraint"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Reservable;

@Entity
class Account {
    @javax.persistence.Id
    private Long id;
    @Reservable
    private Long balance;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def sql = builder.buildCreateTableStatements(entity).join(System.lineSeparator())

        then:
        sql == 'CREATE TABLE "ACCOUNT" ("ID" NUMBER(19) NOT NULL,"BALANCE" NUMBER(19) RESERVABLE NOT NULL, PRIMARY KEY("ID"))'
    }

    void "test build create table with Oracle reservable column supports repeatable validation annotations"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Reservable;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

@Entity
class Account {
    @javax.persistence.Id
    private Long id;
    @Reservable
    @DecimalMin.List({
        @DecimalMin(value = "0.01", inclusive = false),
        @DecimalMin("10")
    })
    @DecimalMax.List({
        @DecimalMax(value = "100", inclusive = false),
        @DecimalMax("200")
    })
    private Long balance;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def sql = builder.buildCreateTableStatements(entity).join(System.lineSeparator())

        then:
        sql == 'CREATE TABLE "ACCOUNT" ("ID" NUMBER(19) NOT NULL,"BALANCE" NUMBER(19) RESERVABLE CONSTRAINT "CK_ACCOUNT_BALANCE_GT_0_01" CHECK ("BALANCE" > 0.01) CONSTRAINT "CK_ACCOUNT_BALANCE_GE_10" CHECK ("BALANCE" >= 10) CONSTRAINT "CK_ACCOUNT_BALANCE_LT_100" CHECK ("BALANCE" < 100) CONSTRAINT "CK_ACCOUNT_BALANCE_LE_200" CHECK ("BALANCE" <= 200) NOT NULL, PRIMARY KEY("ID"))'
    }

    void "test build create table does not derive checks without reservable"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import jakarta.validation.constraints.PositiveOrZero;

@Entity
class Account {
    @javax.persistence.Id
    private Long id;
    @PositiveOrZero
    private Long balance;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def sql = builder.buildCreateTableStatements(entity).join(System.lineSeparator())

        then:
        sql == 'CREATE TABLE "ACCOUNT" ("ID" NUMBER(19) NOT NULL,"BALANCE" NUMBER(19) NOT NULL, PRIMARY KEY("ID"))'
    }

    void "test build create table rejects reservable column for non Oracle dialect"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Reservable;

@Entity
record Account(@javax.persistence.Id Long id, @Reservable Long balance) {
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.POSTGRES)

        when:
        builder.buildCreateTableStatements(entity)

        then:
        def e = thrown(io.micronaut.data.exceptions.MappingException)
        e.message.contains("is only supported for Oracle")
    }

    void "test build create table rejects non numeric reservable column"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Reservable;

@Entity
record Account(@javax.persistence.Id Long id, @Reservable String balance) {
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        builder.buildCreateTableStatements(entity)

        then:
        def e = thrown(io.micronaut.data.exceptions.MappingException)
        e.message.contains("must be numeric")
    }

    void "test build create table rejects reservable column without primary key"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Reservable;

@Entity
record Account(@Reservable Long balance) {
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        builder.buildCreateTableStatements(entity)

        then:
        def e = thrown(io.micronaut.data.exceptions.MappingException)
        e.message.contains("must have a primary key")
    }

    void "test build create table rejects more than 10 reservable columns"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Reservable;

@Entity
record Account(
    @javax.persistence.Id Long id,
    @Reservable Long b1,
    @Reservable Long b2,
    @Reservable Long b3,
    @Reservable Long b4,
    @Reservable Long b5,
    @Reservable Long b6,
    @Reservable Long b7,
    @Reservable Long b8,
    @Reservable Long b9,
    @Reservable Long b10,
    @Reservable Long b11
) {
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        builder.buildCreateTableStatements(entity)

        then:
        def e = thrown(io.micronaut.data.exceptions.MappingException)
        e.message.contains("more than 10 @Reservable properties")
    }

    void "test build create table rejects indexed reservable column"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.Reservable;

@Entity
@Indexes(@Index(name = "idx_account_balance", columns = "balance"))
class Account {
    @javax.persistence.Id
    private Long id;
    @Reservable
    private Long balance;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        builder.buildCreateTableStatements(entity)

        then:
        def e = thrown(io.micronaut.data.exceptions.MappingException)
        e.message.contains("@Reservable column [balance] of entity [test.Account] cannot be indexed")
    }

    void "test build create table supports reservable scalar inside embeddable"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Reservable;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
class Account {
    @javax.persistence.Id
    private Long id;

    @MappedProperty("stats_")
    @Relation(Relation.Kind.EMBEDDED)
    private Stats stats;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Stats getStats() {
        return stats;
    }

    public void setStats(Stats stats) {
        this.stats = stats;
    }
}

@Embeddable
class Stats {
    @Reservable
    @PositiveOrZero
    private Long balance;

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def sql = builder.buildCreateTableStatements(entity).join(System.lineSeparator())

        then:
        sql == 'CREATE TABLE "ACCOUNT" ("ID" NUMBER(19) NOT NULL,"STATS_BALANCE" NUMBER(19) RESERVABLE CONSTRAINT "CK_ACCOUNT_STATS_BALANCE_GE_0" CHECK ("STATS_BALANCE" >= 0) NOT NULL, PRIMARY KEY("ID"))'
    }

    void "test build create table gives embedded reservable checks distinct names"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Reservable;
import jakarta.validation.constraints.PositiveOrZero;

@Entity
class Account {
    @javax.persistence.Id
    private Long id;

    @MappedProperty("stats_")
    @Relation(Relation.Kind.EMBEDDED)
    private Stats stats;

    @MappedProperty("limits_")
    @Relation(Relation.Kind.EMBEDDED)
    private Limits limits;

    Long getId() { return id; }
    void setId(Long id) { this.id = id; }
    Stats getStats() { return stats; }
    void setStats(Stats stats) { this.stats = stats; }
    Limits getLimits() { return limits; }
    void setLimits(Limits limits) { this.limits = limits; }
}

@Embeddable
class Stats {
    @Reservable
    @PositiveOrZero
    private Long balance;

    Long getBalance() { return balance; }
    void setBalance(Long balance) { this.balance = balance; }
}

@Embeddable
class Limits {
    @Reservable
    @PositiveOrZero
    private Long balance;

    Long getBalance() { return balance; }
    void setBalance(Long balance) { this.balance = balance; }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def sql = builder.buildCreateTableStatements(entity).join(System.lineSeparator())

        then:
        sql.contains('CONSTRAINT "CK_ACCOUNT_STATS_BALANCE_GE_0" CHECK ("STATS_BALANCE" >= 0)')
        sql.contains('CONSTRAINT "CK_ACCOUNT_LIMITS_BALANCE_GE_0" CHECK ("LIMITS_BALANCE" >= 0)')
    }

    void "test build create table shortens long reservable check names without collisions"() {
        given:
        def tableName = 'account_' + ('x' * 120)
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Reservable;
import jakarta.validation.constraints.PositiveOrZero;

@MappedEntity("''' + tableName + '''")
record Account(
    @javax.persistence.Id Long id,
    @Reservable @PositiveOrZero Long balance,
    @Reservable @PositiveOrZero Long availableBalance
) {
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        def sql = builder.buildCreateTableStatements(entity).join(System.lineSeparator())
        def constraintNames = (sql =~ /CONSTRAINT "([^"]+)"/).collect { it[1] }

        then:
        constraintNames.size() == 2
        constraintNames.toSet().size() == 2
        constraintNames.every { it.length() <= 128 }
    }

    void "test build create table rejects indexed reservable scalar inside embeddable"() {
        given:
        def entity = buildJpaEntity('test.Account', '''
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Reservable;

@Entity
class Account {
    @javax.persistence.Id
    private Long id;

    @Relation(Relation.Kind.EMBEDDED)
    private Stats stats;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Stats getStats() {
        return stats;
    }

    public void setStats(Stats stats) {
        this.stats = stats;
    }
}

@Embeddable
@Indexes(@Index(name = "idx_stats_balance", columns = "balance"))
class Stats {
    @Reservable
    private Long balance;

    public Long getBalance() {
        return balance;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        builder.buildCreateTableStatements(entity)

        then:
        def e = thrown(io.micronaut.data.exceptions.MappingException)
        e.message.contains("@Reservable column [balance] of entity [test.Account] cannot be indexed")
    }

    void "test build create table rejects reservable relationship property"() {
        given:
        def entity = buildJpaEntity('test.Invoice', '''
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Reservable;

@Entity
class Invoice {
    @javax.persistence.Id
    private Long id;

    @Reservable
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Account account;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }
}

@Entity
class Account {
    @javax.persistence.Id
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:
        builder.buildCreateTableStatements(entity)

        then:
        def e = thrown(io.micronaut.data.exceptions.MappingException)
        e.message.contains("@Reservable property [test.Invoice.account] cannot be a relationship property")
    }


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

    @Unroll
    void "test build create table for Oracle boolean type with #version version"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import io.micronaut.data.annotation.GeneratedValue;

@Entity
class Test {

    @javax.persistence.Id
    @GeneratedValue
    private Long id;

    private Boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE, version)
        def sql = builder.buildBatchCreateTableStatement(List.of(), entity)

        expect:
        sql == statement

        where:
        version                                 | statement
        null                                          | 'CREATE TABLE "TEST" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"ACTIVE" NUMBER(1) NOT NULL)' + System.lineSeparator() +
                                                        'CREATE SEQUENCE "TEST_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE'
        SqlDialectOptions.ORACLE_23_1_0_VERSION | 'CREATE TABLE "TEST" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"ACTIVE" BOOLEAN NOT NULL)' + System.lineSeparator() +
                                                        'CREATE SEQUENCE "TEST_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE'
        "23.4"                                | 'CREATE TABLE "TEST" ("ID" NUMBER(19) NOT NULL PRIMARY KEY,"ACTIVE" BOOLEAN NOT NULL)' + System.lineSeparator() +
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
