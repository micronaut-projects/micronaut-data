
package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.bson.types.ObjectId;

@MappedEntity
public class Person {
    @Id
    @GeneratedValue
    private ObjectId id;
    private String name;
    private int age;

    public Person() {
    }

    public Person(String name, int age) {
        this(null, name, age);
    }

    public Person(ObjectId id, String name, int age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
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
