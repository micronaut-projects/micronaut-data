package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// tag::book[]
@MappedEntity
public class Book {
    @Id
    @GeneratedValue
    private String id;

    private String title;

    // tag::book-many-to-one[]
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private Author author; // <1>
    // end::book-many-to-one[]

    // tag::book-many-to-many[]
    @Relation(value = Relation.Kind.MANY_TO_MANY) // <1>
    private Set<Student> students = new HashSet<>();
    // end::book-many-to-many[]

    private List<Page> pages = new ArrayList<>();

    public Book() {
    }

    public Book(String title) {
        this.title = title;
    }

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

    public List<Page> getPages() {
        return pages;
    }

    public void setPages(List<Page> pages) {
        this.pages = pages;
    }
}
// end::book[]
