
package example;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class BookDto {
    private final String id;
    private final String title;
    private final int pages;

    public BookDto(Book book) {
        this(book.getId().toString(), book.getTitle(), book.getPages());
    }

    @Creator
    public BookDto(String id, String title, int pages) {
        this.id = id;
        this.title = title;
        this.pages = pages;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getPages() {
        return pages;
    }
}
