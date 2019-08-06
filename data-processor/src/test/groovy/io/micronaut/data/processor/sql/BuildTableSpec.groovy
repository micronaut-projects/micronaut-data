package io.micronaut.data.processor.sql

import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.AbstractDataSpec

class BuildTableSpec extends AbstractDataSpec {

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
