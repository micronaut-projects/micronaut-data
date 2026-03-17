package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.HashSet;
import java.util.Set;

// tag::student[]
@MappedEntity
public class Student {
    @Id
    @GeneratedValue
    private String id;

    private String name;

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "students") // <1>
    private Set<Book> books = new HashSet<>();

    public Student() {
    }

    public Student(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Book> getBooks() {
        return books;
    }

    public void setBooks(Set<Book> books) {
        this.books = books;
    }
}
// end::student[]
