
package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::book[]
@MappedEntity
class Book {
    @Id
    @GeneratedValue
    private String id
    private String title
    private int pages

    Book(String title, int pages) {
        this.title = title
        this.pages = pages
    }
    // end::book[]
    String getId() {
        return id
    }

    void setId(String id) {
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
