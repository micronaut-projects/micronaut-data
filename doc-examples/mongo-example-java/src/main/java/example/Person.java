
package example;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.bson.types.ObjectId;

import java.util.List;

@MappedEntity
public class Person {
    @Id
    @GeneratedValue
    private ObjectId id;
    private String name;
    private int age;
    private List<String> interests;

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

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public Person withInterests(@NonNull List<String> interests) {
        this.interests = interests;
        return this;
    }
}
