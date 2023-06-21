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
package io.micronaut.data.annotation

import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.processor.visitors.TestUtils
import io.micronaut.data.tck.entities.Person

class WhereAnnotationSpec extends AbstractDataSpec {

    void "test @Where on entity"() {
        given:
        def repository = buildRepository('test.TestRepository', '''

@Repository
@io.micronaut.context.annotation.Executable
interface TestRepository extends CrudRepository<User, Long> {
    int countByIdGreaterThan(Long id);
    @Join("category")
    List<User> list();

    @Join("category")
    @Where("@.xyz = true")
    @Where("@.abc > 12")
    List<User> findByIdIsNotNull();

    @Join("category")
    @IgnoreWhere
    List<User> findByIdIsNull();
}

@MappedEntity
@Where("@.enabled = true")
class User {
    @Id
    private Long id;
    private boolean enabled;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private Category category;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}

@MappedEntity
@Where("@.archived = true")
class Category {
    @Id
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}
''')
        expect:
        repository.getRequiredMethod("findAll")
                .stringValue(Query).get() == 'SELECT user_ FROM test.User AS user_ WHERE (user_.enabled = true)'
        repository.getRequiredMethod("findById", Long)
                .stringValue(Query).get() == "SELECT user_ FROM test.User AS user_ WHERE (user_.id = :p1 AND (user_.enabled = true))"
        repository.getRequiredMethod("deleteById", Long)
                .stringValue(Query).get() == "DELETE test.User  AS user_ WHERE (user_.id = :p1 AND (user_.enabled = true))"
        repository.getRequiredMethod("deleteAll")
                .stringValue(Query).get() == "DELETE test.User  AS user_ WHERE (user_.enabled = true)"
        repository.getRequiredMethod("count")
                .stringValue(Query).get() == "SELECT COUNT(user_) FROM test.User AS user_ WHERE (user_.enabled = true)"
        repository.getRequiredMethod("countByIdGreaterThan", Long)
                .stringValue(Query).get() == "SELECT COUNT(user_) FROM test.User AS user_ WHERE (user_.id > :p1 AND (user_.enabled = true))"
        repository.getRequiredMethod("list")
                .stringValue(Query).get() == "SELECT user_ FROM test.User AS user_ JOIN FETCH user_.category user_category_ WHERE (user_.enabled = true AND user_category_.archived = true)"
        repository.getRequiredMethod("findAll")
                .stringValue(Query).get() == "SELECT user_ FROM test.User AS user_ WHERE (user_.enabled = true)"
        repository.getRequiredMethod("findByIdIsNotNull")
                .stringValue(Query).get() == "SELECT user_ FROM test.User AS user_ JOIN FETCH user_.category user_category_ WHERE (user_.id IS NOT NULL AND (user_.xyz = true AND user_.abc > 12 AND user_category_.archived = true))"
        repository.getRequiredMethod("findByIdIsNull")
                .stringValue(Query).get() == "SELECT user_ FROM test.User AS user_ JOIN FETCH user_.category user_category_ WHERE (user_.id IS NULL)"
    }

    void "test parameterized @Where declaration - fails compile"() {
        when:"A parameterized @Where is missing an argument definition"
        buildRepository('test.TestRepository', '''
import io.micronaut.data.tck.entities.Person;

@Where("age > :age")
@Repository
interface TestRepository extends GenericRepository<Person, Long> {
    int countByNameLike(String name);
}
''')
        then:"A compiler error"
        def e = thrown(RuntimeException)
        e.message.contains('A @Where(..) definition requires a parameter called [age] which is not present in the method signature.')
    }

    void "test parameterized @Where declaration"() {
        given:
        def repository = buildRepository('test.TestRepository', '''
import io.micronaut.data.tck.entities.Person;

@Where("age > :age")
@Repository
@io.micronaut.context.annotation.Executable
interface TestRepository extends GenericRepository<Person, Long> {
    int countByNameLike(String name, int age);
}
''')
        def method = repository.getRequiredMethod("countByNameLike", String, int.class)
        def query = method.stringValue(Query).get()
        def parameterBinding = TestUtils.getParameterBindingIndexes(method)

        expect:
        query == "SELECT COUNT(person_) FROM $Person.name AS person_ WHERE (person_.name LIKE :p1 AND (age >:p2))"
        parameterBinding.length == 2
    }

    void "test build @Where definition with no parameters at type level"() {
        given:
        def repository = buildRepository('test.TestRepository', '''
import io.micronaut.data.tck.entities.Person;

@Where("person_.age > 18")
@Repository
@io.micronaut.context.annotation.Executable
interface TestRepository extends CrudRepository<Person, Long> {
    int countByNameLike(String name);
}
''')
        expect:
        repository.getRequiredMethod("findAll")
                .stringValue(Query).get() == 'SELECT person_ FROM io.micronaut.data.tck.entities.Person AS person_ WHERE (person_.age > 18)'
        repository.getRequiredMethod("findById", Long)
            .stringValue(Query).get() == "SELECT person_ FROM $Person.name AS person_ WHERE (person_.id = :p1 AND (person_.age > 18))"
        repository.getRequiredMethod("deleteById", Long)
                .stringValue(Query).get() == "DELETE $Person.name  AS person_ WHERE (person_.id = :p1 AND (person_.age > 18))"
        repository.getRequiredMethod("deleteAll")
                .stringValue(Query).get() == "DELETE $Person.name  AS person_ WHERE (person_.age > 18)"
        repository.getRequiredMethod("count")
                .stringValue(Query).get() == "SELECT COUNT(person_) FROM $Person.name AS person_ WHERE (person_.age > 18)"

        repository.getRequiredMethod("countByNameLike", String)
            .stringValue(Query).get() == "SELECT COUNT(person_) FROM $Person.name AS person_ WHERE (person_.name LIKE :p1 AND (person_.age > 18))"
    }

    void "test build @Where on entity and reactive repository"() {
        given:
            def repository = buildRepository('test.TestRepository', '''
import io.micronaut.data.tck.entities.Person;
import io.micronaut.data.repository.reactive.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
@Where(value = "@.deleted = false")
class UserWithWhere {
    @javax.persistence.Id
    private UUID id;
    private String email;
    private Boolean deleted;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}

@Repository
@io.micronaut.context.annotation.Executable
interface TestRepository extends ReactiveStreamsCrudRepository<UserWithWhere, UUID> {
}
''')
        expect:
            repository.getRequiredMethod(method, params as Class[])
                    .stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == interceptor
        where:
            method       || params || interceptor
            "findAll"    || []     || "io.micronaut.data.intercept.reactive.FindAllReactiveInterceptor"
            "findById"   || [UUID] || "io.micronaut.data.intercept.reactive.FindAllReactiveInterceptor"
            "deleteById" || [UUID] || "io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor"
            "deleteAll"  || []     || "io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor"
            "count"      || []     || "io.micronaut.data.intercept.reactive.CountReactiveInterceptor"
    }

    void "test build @Where on entity and reactor reactive repository"() {
        given:
            def repository = buildRepository('test.TestRepository', '''
import io.micronaut.data.repository.reactive.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
@Where(value = "@.deleted = false")
class UserWithWhere {
    @javax.persistence.Id
    private UUID id;
    private String email;
    private Boolean deleted;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}

@Repository
interface TestRepository extends ReactorCrudRepository<UserWithWhere, UUID> {
}
''')
        expect:
            repository.getRequiredMethod(method, params as Class[])
                    .stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == interceptor
        where:
            method       || params || interceptor
            "findAll"    || []     || "io.micronaut.data.intercept.reactive.FindAllReactiveInterceptor"
            "findById"   || [UUID] || "io.micronaut.data.intercept.reactive.FindOneReactiveInterceptor"
            "deleteById" || [UUID] || "io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor"
            "deleteAll"  || []     || "io.micronaut.data.intercept.reactive.DeleteAllReactiveInterceptor"
            "count"      || []     || "io.micronaut.data.intercept.reactive.CountReactiveInterceptor"
    }

    void "test build @Where on entity and async repository"() {
        given:
            def repository = buildRepository('test.TestRepository', '''
import io.micronaut.data.repository.async.*;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "users")
@Where(value = "@.deleted = false")
class UserWithWhere {
    @jakarta.persistence.Id
    private UUID id;
    private String email;
    private Boolean deleted;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }
}

@Repository
interface TestRepository extends AsyncCrudRepository<UserWithWhere, UUID> {
}
''')
        expect:
            repository.getRequiredMethod(method, params as Class[])
                    .stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() == interceptor
        where:
            method       || params || interceptor
            "findAll"    || []     || "io.micronaut.data.intercept.async.FindAllAsyncInterceptor"
            "findById"   || [UUID] || "io.micronaut.data.intercept.async.FindOneAsyncInterceptor"
            "deleteById" || [UUID] || "io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor"
            "deleteAll"  || []     || "io.micronaut.data.intercept.async.DeleteAllAsyncInterceptor"
            "count"      || []     || "io.micronaut.data.intercept.async.CountAsyncInterceptor"
    }

}
