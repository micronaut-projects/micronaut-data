
package example;

import io.micronaut.data.annotation.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;


@Entity
public class Person {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private int age;

    public Person() {
    }

    public Person(String name, int age) {
        this(null, name, age);
    }

    public Person(Long id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

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

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
