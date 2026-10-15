package example;

// tag::person[]
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Node
public class Person {

    @Id
    @AutoPopulated // <1>
    private UUID id;

    private final String name;

    private final Integer born;

    @Relationship("FOLLOWS") // <2>
    private List<Person> follows = new ArrayList<>();

    public Person(String name, Integer born) {
        this.name = name;
        this.born = born;
    }
    // end::person[]

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Integer getBorn() {
        return born;
    }

    public List<Person> getFollows() {
        return follows;
    }

    public void setFollows(List<Person> follows) {
        this.follows = follows;
    }
// tag::person[]
}
// end::person[]
