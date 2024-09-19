package io.micronaut.data.jdbc.h2.joinissue;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.Set;

@MappedEntity("ji_director")
public class Director {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    @Relation(value = Relation.Kind.ONE_TO_MANY, cascade = Relation.Cascade.ALL, mappedBy = "director")
    Set<Movie> movies;

    public Director(String name, @Nullable Set<Movie> movies) {
        this.name = name;
        this.movies = movies;
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

    public Set<Movie> getMovies() {
        return movies;
    }

    public void setMovies(Set<Movie> movies) {
        this.movies = movies;
    }

}

//    @Override
//    public String toString() {
//        return "Director{" +
//                "id=" + id +
//                ", name='" + name + '\'' +
//                ", movies=" + movies +
//                '}';
//    }
//}
