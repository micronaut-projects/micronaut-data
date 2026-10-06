/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.data.annotation.TypeRole
import io.micronaut.data.processor.visitors.AbstractDataSpec

import static io.micronaut.data.processor.visitors.TestUtils.getCountQuery
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterRoles
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JakartaDataQueryTenantSpec extends AbstractDataSpec {

    void "test Jakarta Data @Query methods apply the tenant id like derived finders"() {
        given:
        def repository = buildRepository('test.AccountRepository', """
import io.micronaut.data.annotation.WithTenantId;
import io.micronaut.data.annotation.WithoutTenantId;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Account;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

@JdbcRepository(dialect = Dialect.MYSQL)
@Repository
interface AccountRepository {

    List<Account> findByName(String name);

    @Query("WHERE name = :name")
    List<Account> byName(String name);

    @Query("FROM Account")
    List<Account> all();

    @WithoutTenantId
    @Query("FROM Account")
    List<Account> allTenants();

    @WithTenantId("foo")
    @Query("FROM Account WHERE name = :name")
    List<Account> byNameForFoo(String name);

    @Query("WHERE name = :name")
    Page<Account> page(String name, PageRequest pageRequest);

    @Query("UPDATE Account SET name = :newName WHERE name = :name")
    long rename(String name, String newName);

    @Query("DELETE FROM Account WHERE name = :name")
    long removeByName(String name);
}
""")

        when:
        def findByName = repository.getRequiredMethod("findByName", String)
        def byName = repository.getRequiredMethod("byName", String)

        then: "the derived finder applies the tenant id"
        getQuery(findByName) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_ WHERE (account_.`name` = ? AND account_.`tenancy` = ?)'
        getParameterPropertyPaths(findByName) == ["name", "tenancy"] as String[]

        and: "the Jakarta Data query applies it too"
        getQuery(byName) == getQuery(findByName)
        getParameterPropertyPaths(byName) == ["name", "tenancy"] as String[]
        getQuery(repository.getRequiredMethod("all")) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_ WHERE (account_.`tenancy` = ?)'
        getQuery(repository.getRequiredMethod("allTenants")) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_'
        getQuery(repository.getRequiredMethod("byNameForFoo", String)) == 'SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_ WHERE (account_.`name` = ? AND account_.`tenancy` = \'foo\')'

        and: "the count query of a page is filtered by the tenant id"
        def page = repository.findPossibleMethods("page").findFirst().get()
        getQuery(page).startsWith('SELECT account_.`id`,account_.`name`,account_.`tenancy` FROM `account` account_ WHERE (account_.`name` = ? AND account_.`tenancy` = ?)')
        getCountQuery(page) == 'SELECT COUNT(*) FROM `account` account_ WHERE (account_.`name` = ? AND account_.`tenancy` = ?)'

        and: "updates and deletes are filtered by the tenant id"
        getQuery(repository.getRequiredMethod("rename", String, String)) == 'UPDATE `account` SET `name`=? WHERE (`name` = ? AND `tenancy` = ?)'
        getQuery(repository.getRequiredMethod("removeByName", String)) == 'DELETE  FROM `account`  WHERE (`name` = ? AND `tenancy` = ?)'
    }

    void "test Jakarta Data @Query with Sort and Limit registers the same parameter roles as a derived finder"() {
        given:
        def repository = buildRepository('test.BookRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;
import jakarta.data.Limit;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;

@JdbcRepository(dialect = Dialect.H2)
@Repository
interface BookRepository {

    List<Book> findByTitle(String title, Sort<Book> sort, Limit limit);

    @Query("WHERE title = :title")
    List<Book> byTitle(String title, Sort<Book> sort, Limit limit);

    @Query("WHERE title = :title")
    List<Book> byTitleOrdered(String title, Order<Book> order, Limit limit);

    @Query("WHERE title = :title")
    List<Book> byTitleLimited(String title, Limit limit);
}
""")

        when:
        def findByTitle = repository.findPossibleMethods("findByTitle").findFirst().get()
        def byTitle = repository.findPossibleMethods("byTitle").findFirst().get()
        def byTitleOrdered = repository.findPossibleMethods("byTitleOrdered").findFirst().get()
        def byTitleLimited = repository.findPossibleMethods("byTitleLimited").findFirst().get()

        then: "the sort role binding appends the order and the limit at runtime"
        getParameterRoles(findByTitle) == [null, TypeRole.SORT] as String[]
        getParameterRoles(byTitle) == getParameterRoles(findByTitle)
        getParameterRoles(byTitleOrdered) == getParameterRoles(findByTitle)
        getQuery(byTitle) == getQuery(findByTitle)
        !getQuery(byTitle).contains("LIMIT")

        and: "a limit alone is registered in the limit role"
        getParameterRoles(byTitleLimited) == [null, TypeRole.LIMIT] as String[]
    }
}
