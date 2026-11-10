package example;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.graph.annotation.Node;

import java.time.LocalDate;

@Node("Movie")
public class MixedMovie {
    @Id
    private final String title;
    @MappedProperty("tagline")
    private final String description;
    private final Integer released;
    private Long votes;
    private Double rating;
    private Boolean colour;
    private String director;
    private String studio;
    private Long budget;
    private LocalDate premiere;

    public MixedMovie(String title, String description, Integer released) {
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

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public Boolean getColour() {
        return colour;
    }

    public void setColour(Boolean colour) {
        this.colour = colour;
    }

    public String getDirector() {
        return director;
    }

    public void setDirector(String director) {
        this.director = director;
    }

    public String getStudio() {
        return studio;
    }

    public void setStudio(String studio) {
        this.studio = studio;
    }

    public Long getBudget() {
        return budget;
    }

    public void setBudget(Long budget) {
        this.budget = budget;
    }

    public LocalDate getPremiere() {
        return premiere;
    }

    public void setPremiere(LocalDate premiere) {
        this.premiere = premiere;
    }
}
