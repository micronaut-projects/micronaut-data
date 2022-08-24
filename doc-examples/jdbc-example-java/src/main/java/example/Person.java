
package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;


@Entity
public class Person {
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private int age;
    @MappedProperty(value = "long_name_column_legacy_system", alias = "long_name")
    private String longName;

    public Person() {
    }

    public Person(String name, int age, String longName) {
        this(null, name, age, longName);
    }

    public Person(Long id, String name, int age, String longName) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.longName = longName;
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

    public String getLongName() {
        return longName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }
}
