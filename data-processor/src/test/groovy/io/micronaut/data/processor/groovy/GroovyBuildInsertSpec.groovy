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
package io.micronaut.data.processor.groovy

import io.micronaut.data.annotation.Query
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
                .stringValue(Query)
                .orElse(null) == 'INSERT INTO "shelf_book" ("shelf_id","book_id") VALUES (?,?)'
    }
}
