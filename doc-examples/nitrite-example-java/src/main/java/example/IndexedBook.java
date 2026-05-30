package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.nitrite.annotation.FullTextIndex;
import io.micronaut.data.nitrite.annotation.SpatialIndex;
import org.locationtech.jts.geom.Geometry;

@MappedEntity
// tag::compound-index[]
@Index(name = "book_title_pages", columns = {"title", "pages"})
// end::compound-index[]
public class IndexedBook {
    @Id
    @GeneratedValue
    private String id;

    // tag::property-index[]
    @Index(columns = "title")
    // end::property-index[]
    private String title;

    private int pages;

    @FullTextIndex
    private String description;

    // tag::spatial-index[]
    @SpatialIndex
    // end::spatial-index[]
    private Geometry location;

    public IndexedBook() {
    }

    public IndexedBook(String title, int pages) {
        this.title = title;
        this.pages = pages;
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

    public int getPages() {
        return pages;
    }

    public void setPages(int pages) {
        this.pages = pages;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Geometry getLocation() {
        return location;
    }

    public void setLocation(Geometry location) {
        this.location = location;
    }
}
