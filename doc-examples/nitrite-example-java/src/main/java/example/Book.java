package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.HashSet;
import java.util.Set;

// tag::book[]
@MappedEntity
public class Book {
    @Id
    @GeneratedValue
    private String id;

    private String title;
    // end::book[]

    public Book() {
    }

    // tag::book[]
    public Book(String title) {
        this.title = title;
    }
    // end::book[]

    // tag::book-many-to-one[]
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private Author author; // <1>
    // end::book-many-to-one[]

    // tag::book-many-to-many[]
    @Relation(value = Relation.Kind.MANY_TO_MANY)
    private Set<Student> students = new HashSet<>(); // <1>
    // end::book-many-to-many[]

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public Set<Student> getStudents() {
        return students;
    }

    public void setStudents(Set<Student> students) {
        this.students = students;
    }
// tag::book[]
}
// end::book[]
