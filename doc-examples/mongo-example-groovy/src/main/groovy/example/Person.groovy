
package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import org.bson.types.ObjectId

@MappedEntity
class Person {
    @Id
    @GeneratedValue
    private ObjectId id
    private String name
    private int age
    private List<String> interests;

    Person() {
    }

    Person(String name, int age) {
        this(null, name, age)
    }

    Person(ObjectId id, String name, int age) {
        this.id = id
        this.name = name
        this.age = age
    }

    ObjectId getId() {
        return id
    }

    void setId(ObjectId id) {
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

    List<String> getInterests() {
        return interests
    }

    void setInterests(List<String> interests) {
        this.interests = interests
    }

    Person withInterests(@NonNull List<String> interests) {
        this.interests = interests
        return this
    }
}
