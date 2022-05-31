
package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import org.bson.types.ObjectId;

@MappedEntity
public record Person(
        @Id
        @GeneratedValue ObjectId id,
        String name,
        int age) {

    public Person(String name, int age) {
        this(null, name, age);
    }

}
