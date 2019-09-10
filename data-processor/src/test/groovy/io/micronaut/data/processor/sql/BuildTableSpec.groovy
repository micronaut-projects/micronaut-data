package io.micronaut.data.processor.sql

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Requires

@Requires({ javaVersion <= 1.8 })
class BuildTableSpec extends AbstractDataSpec {

    void "test build create table for JSON type"() {
        given:
        def entity = buildJpaEntity('test.Test', '''
import java.util.Map;

@Entity
class Test {

    @javax.persistence.Id
    @GeneratedValue
    private Long id;

    @io.micronaut.data.annotation.TypeDef(type=io.micronaut.data.model.DataType.JSON)
    private Map json;
    
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
}
''')
        SqlQueryBuilder builder = new SqlQueryBuilder(dialect)
        def sql = builder.buildBatchCreateTableStatement(entity)

        expect:
        sql == statement

        where:
        dialect          | statement
        Dialect.H2       | 'CREATE TABLE test (id BIGINT AUTO_INCREMENT PRIMARY KEY,json JSON NOT NULL);'
        Dialect.MYSQL    | 'CREATE TABLE test (id BIGINT AUTO_INCREMENT PRIMARY KEY,json JSON NOT NULL);'
        Dialect.POSTGRES | 'CREATE TABLE test (id BIGINT GENERATED ALWAYS AS IDENTITY,json JSONB NOT NULL);'
        Dialect.ORACLE   | 'CREATE TABLE test (id BIGINT AUTO_INCREMENT PRIMARY KEY,json CLOB NOT NULL);'
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
        SqlQueryBuilder builder = new SqlQueryBuilder()
        def sql = builder.buildBatchCreateTableStatement(entity)

        then:
        sql == 'CREATE TABLE test (id BIGINT AUTO_INCREMENT PRIMARY KEY,date_created TIMESTAMP WITH TIME ZONE);'
    }
}
