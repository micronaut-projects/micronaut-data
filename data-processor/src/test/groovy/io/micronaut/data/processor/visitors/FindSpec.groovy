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
package io.micronaut.data.processor.visitors


import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.FindByIdInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Issue
import spock.lang.Unroll

class FindSpec extends AbstractDataSpec {

    void "test find by with overlapping property paths 2"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.PlayerRepository', '''import javax.persistence.GenerationType;

@javax.persistence.Entity
@javax.persistence.Table(name = "player")
class Player {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
    private Team team;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Team getTeam() {
        return team;
    }
}

@javax.persistence.Entity
@javax.persistence.Table(name = "team")
class Team {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy= GenerationType.IDENTITY)
    private Long id;
    private Integer externalTeamId;
    private String externalTeam;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public Integer getExternalTeamId() {
        return externalTeamId;
    }

    public void setExternalTeamId(Integer externalTeamId) {
        this.externalTeamId = externalTeamId;
    }

    public String getExternalTeam() {
        return externalTeam;
    }

    public void setExternalTeam(String externalTeam) {
        this.externalTeam = externalTeam;
    }
}

@Repository
interface PlayerRepository extends GenericRepository<Player, Long> {

    Collection<Player> findByName(String name);

    Collection<Player> findByTeamName(String name);

    Collection<Player> findByTeamId(Integer id);

    Collection<Player> findByTeamExternalTeamId(Integer id);

    Collection<Player> findByTeamExternalTeam(String team);
}
''')
        expect:
        beanDefinition != null
    }

    void "test find by with overlapping property paths"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.DeviceInfoRepository', """import io.micronaut.context.annotation.Executable;

@javax.persistence.Entity
class DeviceInfo {

    @javax.persistence.Id
    @javax.persistence.GeneratedValue(strategy=javax.persistence.GenerationType.IDENTITY)
    private Long id;

    private String manufacturerDeviceId;

    @javax.persistence.ManyToOne
    @javax.persistence.JoinColumn(name="manufacturer_id")
    public DeviceManufacturer manufacturer;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getManufacturerDeviceId() {
        return manufacturerDeviceId;
    }

    public void setManufacturerDeviceId(String manufacturerDeviceId) {
        this.manufacturerDeviceId = manufacturerDeviceId;
    }

    public DeviceManufacturer getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(DeviceManufacturer manufacturerDeviceId) {
        this.manufacturer = manufacturer;
    }
}

@javax.persistence.Entity
class DeviceManufacturer {

    @javax.persistence.Id
    private Long id;
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}

@Repository
@Executable
interface DeviceInfoRepository extends GenericRepository<DeviceInfo, Long> {

    DeviceInfo findByManufacturerDeviceId(String id);
}

""")
        def findByManufacturerDeviceId = beanDefinition.getRequiredMethod("findByManufacturerDeviceId", String)

        expect:
        findByManufacturerDeviceId != null
    }

    void "test find order by"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.PlayerRepository', """

${playerTeamModel()}

@Repository
@io.micronaut.context.annotation.Executable
interface PlayerRepository extends GenericRepository<Player, Integer> {

    Collection<Player> findAllOrderByName();
    Collection<Player> findAllOrderByNameAsc();
    Collection<Player> findAllOrderByNameDesc();
}

""")
        def findAllOrderByName = beanDefinition.getRequiredMethod("findAllOrderByName")
        def findAllOrderByNameAsc = beanDefinition.getRequiredMethod("findAllOrderByNameAsc")
        def findAllOrderByNameDesc = beanDefinition.getRequiredMethod("findAllOrderByNameDesc")

        expect:
        findAllOrderByName.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ ORDER BY player_.name ASC'
        findAllOrderByNameAsc.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ ORDER BY player_.name ASC'
        findAllOrderByNameDesc.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ ORDER BY player_.name DESC'
    }

    void "test find by association id - singled ended"() {
        given:
        BeanDefinition beanDefinition = buildRepository('test.PlayerRepository', """

${playerTeamModel()}

@Repository
@io.micronaut.context.annotation.Executable
interface PlayerRepository extends GenericRepository<Player, Integer> {

    Collection<Player> findByTeamName(String name);

    Collection<Player> findByTeamId(Integer id);
}

""")
        def findByTeamName = beanDefinition.getRequiredMethod("findByTeamName", String)
        def findByTeamId = beanDefinition.getRequiredMethod("findByTeamId", Integer)

        expect:
        findByTeamName.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ JOIN player_.team player_team_ WHERE (player_team_.name = :p1)'
        findByTeamId.stringValue(Query).get() == 'SELECT player_ FROM test.Player AS player_ WHERE (player_.team.id = :p1)'
    }

    void "test find method match"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.data.model.entities.Person;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.GenericRepository;

@Repository
@Executable
interface MyInterface extends GenericRepository<Person, Long> {

    Person find(Long id);

    Person find(Long id, String name);

    Person findById(Long id);

    Iterable<Person> findByIds(Iterable<Long> ids);
}
""")
        def alias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))

        when: "the list method is retrieved"

        def findMethod = beanDefinition.getRequiredMethod("find", Long)
        def findMethod2 = beanDefinition.getRequiredMethod("find", Long, String)
        def findMethod3 = beanDefinition.getRequiredMethod("findById", Long)
        def findByIds = beanDefinition.getRequiredMethod("findByIds", Iterable.class)

        def findAnn = findMethod.synthesize(DataMethod)
        def findAnn2 = findMethod2.synthesize(DataMethod)
        def findAnn3 = findMethod3.synthesize(DataMethod)
        def findByIdsAnn = findByIds.synthesize(DataMethod)

        then: "it is configured correctly"
        findAnn.interceptor() == FindByIdInterceptor
        findAnn3.interceptor() == FindByIdInterceptor
        findAnn2.interceptor() == FindOneInterceptor
        findByIdsAnn.interceptor() == FindAllInterceptor
        findByIds.synthesize(Query).value() == "SELECT $alias FROM io.micronaut.data.model.entities.Person AS $alias WHERE (${alias}.id IN (:p1))"
    }

    String playerTeamModel() {
        '''
@MappedEntity
class Player {

    @GeneratedValue
    @Id
    private Integer id;
    private String name;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Team team;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Team getTeam() {
        return this.team;
    }
}

@MappedEntity
class Team {
    @GeneratedValue
    @Id
    private Integer id;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}'''
    }

    @Issue('#632')
    void "test embedded object on finders"() {
        given:
        def repository = buildRepository('test.RestaurantRepository', """
import io.micronaut.context.annotation.Executable;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.UUID;

@JdbcRepository(dialect= Dialect.MYSQL)
@Executable
interface RestaurantRepository extends CrudRepository<Restaurant, Long> {

    Restaurant findByAddressZipCodeLike(String name);

    Restaurant findByAddressZipCodeIlike(String name);
}
""")

        def query1 = repository.getRequiredMethod("findByAddressZipCodeLike", String)
                .stringValue(Query).get()
        def query2 = repository.getRequiredMethod("findByAddressZipCodeIlike", String)
                .stringValue(Query).get()

        expect: "The query contains the correct embedded property name"
        query1.contains('WHERE (restaurant_.`address_zip_code` LIKE ?')
        query2.contains('WHERE (LOWER(restaurant_.`address_zip_code`) LIKE LOWER(?)')
    }

    void "test top"() {
        given:
            def repository = buildRepository('test.TestRepository', """
import io.micronaut.context.annotation.Executable;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect= Dialect.POSTGRES)
@Executable
interface TestRepository extends CrudRepository<Book, Long> {

    List<Book> findTop30OrderByTitle();

}
""")
        when:
            def method = repository.findPossibleMethods("findTop30OrderByTitle").findFirst().get()
        then:
            method.stringValue(Query).get() == 'SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated" FROM "book" book_ ORDER BY book_."title" ASC LIMIT 30'
            method.intValue(DataMethod, DataMethod.META_MEMBER_PAGE_SIZE).isEmpty()
            method.intValue(DataMethod, DataMethod.META_MEMBER_LIMIT).isEmpty()
    }

    void "test top with sort"() {
        given:
            def repository = buildRepository('test.TestRepository', """
import io.micronaut.context.annotation.Executable;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect= Dialect.POSTGRES)
@Executable
interface TestRepository extends CrudRepository<Book, Long> {

    List<Book> findTop30OrderByTitle(Sort sort);

}
""")
        when:
            def method = repository.findPossibleMethods("findTop30OrderByTitle").findFirst().get()
        then:
            method.stringValue(Query).get() == 'SELECT book_."id",book_."author_id",book_."genre_id",book_."title",book_."total_pages",book_."publisher_id",book_."last_updated" FROM "book" book_'
            method.intValue(DataMethod, DataMethod.META_MEMBER_LIMIT).isPresent()
    }

    void "test top JPA"() {
        given:
            def repository = buildRepository('test.TestRepository', """
import io.micronaut.context.annotation.Executable;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

@Repository
interface TestRepository extends CrudRepository<Book, Long> {

    List<Book> findTop30OrderByTitle();

}
""")
        when:
            def method = repository.findPossibleMethods("findTop30OrderByTitle").findFirst().get()
        then:
            method.stringValue(Query).get() == 'SELECT book_ FROM io.micronaut.data.tck.entities.Book AS book_ ORDER BY book_.title ASC'
            method.intValue(DataMethod, DataMethod.META_MEMBER_LIMIT).getAsInt() == 30
    }

    void "test project association"() {
        given:
            def repository = buildRepository('test.TestRepository', """
import io.micronaut.context.annotation.Executable;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.entities.Author;

@JdbcRepository(dialect= Dialect.POSTGRES)
@Executable
interface TestRepository extends CrudRepository<Book, Long> {

    Author findAuthorById(@Id Long id);

}
""")
        when:
            def method = repository.findPossibleMethods("findAuthorById").findFirst().get()
        then:
            method.stringValue(Query).get() == 'SELECT book_author_."id",book_author_."name",book_author_."nick_name" FROM "book" book_ INNER JOIN "author" book_author_ ON book_."author_id"=book_author_."id" WHERE (book_."id" = ?)'
    }

    @Unroll
    void "test projection #projection find"() {
        given:
            def repository = buildRepository('test.TestRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Person;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface TestRepository extends CrudRepository<Person, Long> {

    int find${projection}AgeByNameLike(String name);

}
""")
        when:
            def method = repository.findPossibleMethods("find${projection}AgeByNameLike").findFirst().get()
        then:
            method.stringValue(Query).get() == """SELECT ${projection.toUpperCase()}(person_."age") FROM "person" person_ WHERE (person_."name" LIKE ?)"""
        where:
            projection << ['Min', 'Max', 'Sum', "Avg"]
    }

    void "test find for update"() {
        given:
        def repository = buildRepository('test.TestRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect= Dialect.POSTGRES)
@io.micronaut.context.annotation.Executable
interface TestRepository extends CrudRepository<Book, Long> {

    @Join("author")
    $returnType.simpleName<Book> $methodName($arguments);
}
""")
        expect:
        repository.findPossibleMethods(methodName).findFirst().get()
                .stringValue(Query).get().endsWith(" FOR UPDATE")

        where:
        returnType | methodName                                 | arguments
        Optional   | 'findByIdForUpdate'                        | 'Long id'
        List       | 'findAllForUpdate'                         | ''
        List       | 'findAllByTitleForUpdate'                  | 'String title'
        List       | 'findAllOrderByTotalPagesForUpdate'        | ''
        List       | 'findAllByTitleOrderByTotalPagesForUpdate' | 'String title'
        Optional   | 'findFirstForUpdate'                       | ''
        List       | 'findTop10ForUpdate'                       | ''
    }

    void "test find for update sql server"() {
        given:
        def repository = buildRepository('test.TestRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialect= Dialect.SQL_SERVER)
@io.micronaut.context.annotation.Executable
interface TestRepository extends CrudRepository<Book, Long> {

    @Join("author")
    $returnType.simpleName<Book> $methodName($arguments);
}
""")
        expect:
        def query = repository.findPossibleMethods(methodName).findFirst().get().stringValue(Query).get()
        query.contains("[book] book_ WITH (UPDLOCK, ROWLOCK) INNER JOIN [author] book_author_ WITH (UPDLOCK, ROWLOCK)")
        !query.endsWith("FOR UPDATE")

        where:
        returnType | methodName                                 | arguments
        Optional   | 'findByIdForUpdate'                        | 'Long id'
        List       | 'findAllForUpdate'                         | ''
        List       | 'findAllByTitleForUpdate'                  | 'String title'
        List       | 'findAllOrderByTotalPagesForUpdate'        | ''
        List       | 'findAllByTitleOrderByTotalPagesForUpdate' | 'String title'
        Optional   | 'findFirstForUpdate'                       | ''
        List       | 'findTop10ForUpdate'                       | ''
    }

    void "test find for update jpa"() {
        when:
        buildRepository('test.TestRepository', """
import io.micronaut.data.tck.entities.Book;

@Repository
@io.micronaut.context.annotation.Executable
interface TestRepository extends CrudRepository<Book, Long> {

    $returnType.simpleName<Book> $methodName($arguments);
}
""")
        then:
        RuntimeException exception = thrown()
        exception.message.contains("For update not supported for current query builder: JpaQueryBuilder")

        where:
        returnType | methodName                          | arguments      | nonExistentProperty
        Optional   | 'findByIdForUpdate'                 | 'Long id'      | 'idForUpdate'
        List       | 'findAllForUpdate'                  | ''             | 'allForUpdate'
        List       | 'findAllByTitleForUpdate'           | 'String title' | 'titleForUpdate'
        List       | 'findAllOrderByTotalPagesForUpdate' | ''             | 'totalPagesForUpdate'
        Optional   | 'findFirstForUpdate'                | ''             | 'firstForUpdate'
        List       | 'findTop10ForUpdate'                | ''             | 'top10ForUpdate'
    }
}
