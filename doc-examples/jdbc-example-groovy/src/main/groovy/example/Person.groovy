
package example

import io.micronaut.data.annotation.Id
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue


@Entity
class Person {
    @Id
    @GeneratedValue
    private Long id
    private String name
    private int age

    Person() {
    }

    Person(String name, int age) {
        this(null, name, age)
    }

    Person(Long id, String name, int age) {
        this.id = id
        this.name = name
        this.age = age
    }

    Long getId() {
        return id
    }

    void setId(Long id) {
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
