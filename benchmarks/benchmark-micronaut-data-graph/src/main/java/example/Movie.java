package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.graph.annotation.Node;

@Node("Movie")
public class Movie {

    @Id
    private final String title;
    @MappedProperty("tagline")
    private final String description;
    private final Integer released;
    private Long votes;

    public Movie(String title, String description, Integer released) {
        this.title = title;
        this.description = description;
        this.released = released;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getReleased() {
        return released;
    }

    public Long getVotes() {
        return votes;
    }

    public void setVotes(Long votes) {
        this.votes = votes;
    }
}
