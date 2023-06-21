
package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@MappedEntity
class Person {
    @Id
    @GeneratedValue
    private String id
    private String name
    private int age

    Person() {
    }

    Person(String name, int age) {
        this(null, name, age)
    }

    Person(String id, String name, int age) {
        this.id = id
        this.name = name
        this.age = age
    }

    String getId() {
        return id
    }

    void setId(String id) {
        this.id = id
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    int getAge() {
        return age
    }

    void setAge(int age) {
        this.age = age
    }
}
