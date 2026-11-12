package example;

// tag::movie[]
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.annotation.Node;
import io.micronaut.data.graph.annotation.Relationship;

import java.util.ArrayList;
import java.util.List;

@Node // <1>
public class Movie {

    @Id // <2>
    private final String title;

    @MappedProperty("tagline") // <3>
    private final String description;

    private final Integer released;

    @Relationship(type = "ACTED_IN", direction = Direction.INCOMING) // <4>
    private List<Role> roles = new ArrayList<>();

    @Relationship(type = "DIRECTED", direction = Direction.INCOMING)
    private List<Person> directors = new ArrayList<>();

    public Movie(String title, String description, Integer released) {
        this.title = title;
        this.description = description;
        this.released = released;
    }
    // end::movie[]

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getReleased() {
        return released;
    }

    public List<Role> getRoles() {
        return roles;
    }

    public void setRoles(List<Role> roles) {
        this.roles = roles;
    }

    public List<Person> getDirectors() {
        return directors;
    }

    public void setDirectors(List<Person> directors) {
        this.directors = directors;
    }
// tag::movie[]
}
// end::movie[]
