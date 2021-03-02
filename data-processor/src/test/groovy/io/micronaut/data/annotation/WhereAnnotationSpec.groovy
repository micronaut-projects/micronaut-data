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
                .stringValue(Query).get() == "SELECT user_ FROM test.User AS user_ JOIN FETCH user_.category user_category_ WHERE (user_.enabled = true)"
        repository.getRequiredMethod("findAll")
                .stringValue(Query).get() == "SELECT user_ FROM test.User AS user_ WHERE (user_.enabled = true)"
        repository.getRequiredMethod("findByIdIsNotNull")
                .stringValue(Query).get() == "SELECT user_ FROM test.User AS user_ JOIN FETCH user_.category user_category_ WHERE (user_.id IS NOT NULL  AND (user_.xyz = true AND user_.abc > 12))"
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
        def parameterBinding = method.getValue(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING, int[].class).get()

        expect:
        query == "SELECT COUNT(person_) FROM $Person.name AS person_ WHERE (person_.name like :p1 AND (age >:p2))"
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
            .stringValue(Query).get() == "SELECT COUNT(person_) FROM $Person.name AS person_ WHERE (person_.name like :p1 AND (person_.age > 18))"
    }
}
