
package example

import javax.persistence.*

@Entity
class Book {
    @Id
    @GeneratedValue
    Long id
    private String title
    private int pages

    Book(String title, int pages) {
        this.title = title
        this.pages = pages
    }

    String getTitle() {
        return title
    }

    int getPages() {
        return pages
    }
}
