package io.micronaut.data.processor.groovy

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.PendingFeature

class GroovyBuildInsertSpec extends AbstractGroovyBeanDefinitionSpec {

    @PendingFeature(reason = "Groovy currently has problems when it comes to reading the AST of existing compile classes. This test fails because the annotation metadata of compiled classes is not represented in the Groovy AST")
    void "test build SQL insert statement for entity with no ID"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.TestShelfBookRepository' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.tck.entities.Shelf;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.ShelfBook;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class)
interface TestShelfBookRepository extends io.micronaut.data.tck.repositories.ShelfBookRepository {

}
""")

        expect:
        beanDefinition.findPossibleMethods("save")
                .findFirst().get()
                .stringValue(DataMethod.class, DataMethod.META_MEMBER_INSERT_STMT)
                .orElse(null) == 'INSERT INTO "shelf_book" ("shelf_id","book_id") VALUES (?,?)'
    }
}
