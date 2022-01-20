
package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import org.bson.types.ObjectId

// tag::book[]
@MappedEntity
class Book {
    @Id
    @GeneratedValue
    private ObjectId id
    private String title
    private int pages

    Book(String title, int pages) {
        this.title = title
        this.pages = pages
    }
    // end::book[]
    ObjectId getId() {
        return id
    }

    void setId(ObjectId id) {
        this.id = id
    }

    void setTitle(String title) {
        this.title = title
    }

    void setPages(int pages) {
        this.pages = pages
    }

    String getTitle() {
        return title
    }

    int getPages() {
        return pages
    }
    // tag::book[]
    //...
}
// end::book[]
