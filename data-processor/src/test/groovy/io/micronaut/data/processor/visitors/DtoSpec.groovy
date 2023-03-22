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
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.entities.Person
import io.micronaut.data.model.query.builder.jpa.JpaQueryBuilder

class DtoSpec extends AbstractDataSpec {

    void "test build DTO with raw @Query method doesn't fail to compile"() {
        when:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import io.micronaut.core.annotation.Introspected;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    @Query("select count(*) as total from person")
    TotalDto getTotal();
}

@Introspected
class TotalDto {
    private long total;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

}
""")

        then:
        repository != null
    }

    void "test build a list of DTO with raw @Query method doesn't fail to compile"() {
        when:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import io.micronaut.core.annotation.Introspected;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    @Query("select name as fullName from person")
    List<FullNameDto> getFullName();
}

@Introspected
class FullNameDto {
    private String fullName;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

}
""")

        then:
        repository != null
    }

    void "test build repository with DTO projection - invalid types"() {
        when:
        buildRepository('test.MyInterface' , """

import io.micronaut.data.model.entities.Person;
import io.micronaut.core.annotation.Introspected;

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    List<PersonDto> list(String name);
}

@Introspected
class PersonDto {
    private int name;

    public int getName() {
        return name;
    }

    public void setName(int name) {
        this.name = name;
    }

}
""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Property [name] of type [int] is not compatible with equivalent property of type [java.lang.String] declared in entity: io.micronaut.data.model.entities.Person')
    }


    void "test build repository with DTO projection"() {
        when:
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.Person;
import io.micronaut.core.annotation.Introspected;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Person, Long> {

    List<PersonDto> list(String name);

    PersonDto find(String name);

    Page<PersonDto> searchByNameLike(String title, Pageable pageable);

    java.util.stream.Stream<PersonDto> queryByNameLike(String title, Pageable pageable);

    @Query("select * from person p where p.name = :name")
    Optional<PersonDto> findByNameWithQuery(String name);
}

@Introspected
class PersonDto {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
""")
        def alias = new JpaQueryBuilder().getAliasName(PersistentEntity.of(Person))

        then:
        repository != null
        def method = repository.getRequiredMethod("list", String)
        def ann = method.synthesize(DataMethod)
        ann.resultType().name.contains("PersonDto")
        ann.rootEntity() == Person
        method.synthesize(Query).value() == "SELECT ${alias}.name AS name FROM $Person.name AS $alias WHERE (${alias}.name = :p1)"
        method.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)

        and:
        def findMethod = repository.getRequiredMethod("find", String)
        findMethod.synthesize(DataMethod).resultType().simpleName == "PersonDto"

        and:
        def pageMethod = repository.getRequiredMethod("searchByNameLike", String, Pageable)
        pageMethod.synthesize(DataMethod).resultType().simpleName == "PersonDto"

        and:
        def streamMethod = repository.getRequiredMethod("queryByNameLike", String, Pageable)
        streamMethod.synthesize(DataMethod).resultType().simpleName == "PersonDto"

        and:
        def queryMethod = repository.getRequiredMethod("findByNameWithQuery", String)
        queryMethod.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)

    }

    void "test build repository with DTO projection with association"() {
        when:
        def repository = buildJpaRepository('test.MyInterface', """

import io.micronaut.core.annotation.Introspected;
import jakarta.persistence.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Join;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Author, Long> {

    List<AuthorDto> listAll();

    @Join(value = "books", type = Join.Type.LEFT)
    List<AuthorDto> findAll();

}

@Entity
class Author {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    @ManyToMany
    private Set<Book> books;

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

    public Set<Book> getBooks() {
        return books;
    }

    public void setBooks(Set<Book> books) {
        this.books = books;
    }
}

@Entity
class Book {
    @Id
    @GeneratedValue
    private Long id;
    private String title;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}


@Introspected
class AuthorDto {
    private String name;
    private Set<Book> books;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Book> getBooks() {
        return books;
    }

    public void setBooks(Set<Book> books) {
        this.books = books;
    }

}
""")
        then:
        repository != null

        def listAllMethod = repository.getRequiredMethod("listAll")
        listAllMethod.synthesize(DataMethod).resultType().name.contains("AuthorDto")
        listAllMethod.synthesize(Query).value() == "SELECT author_.name AS name FROM test.Author AS author_"
        listAllMethod.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)

        def findAllMethod = repository.getRequiredMethod("findAll")
        findAllMethod.synthesize(DataMethod).resultType().name.contains("AuthorDto")
        findAllMethod.synthesize(Query).value() == "SELECT author_.name AS name,author_books_ AS books FROM test.Author AS author_ LEFT JOIN author_.books author_books_"
        findAllMethod.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)
    }

    void "test build repository with DTO projection 2"() {
        when:
            def repository = buildJpaRepository('test.MyInterface', """

import io.micronaut.core.annotation.Introspected;
import jakarta.persistence.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Join;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<Author, Long> {

    AuthorDto findFirstNameAndLastNameByIdAndEmailIn(Long id, Collection<String> email);

}

@Entity
class Author {

    @Id
    @GeneratedValue
    private Long id;
    private String firstName;
    private String lastName;
    private String email;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

      public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

}


@Introspected
class AuthorDto {
    private String firstName;
    private String lastName;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

}
""")
        then:
            repository != null

            def listAllMethod = repository.getRequiredMethod("findFirstNameAndLastNameByIdAndEmailIn", Long.class, Collection.class)
            listAllMethod.synthesize(DataMethod).resultType().name.contains("AuthorDto")
            listAllMethod.synthesize(Query).value() == "SELECT author_.firstName AS firstName,author_.lastName AS lastName FROM test.Author AS author_ WHERE (author_.id = :p1 AND author_.email IN (:p2))"
            listAllMethod.isTrue(DataMethod, DataMethod.META_MEMBER_DTO)
    }

}
