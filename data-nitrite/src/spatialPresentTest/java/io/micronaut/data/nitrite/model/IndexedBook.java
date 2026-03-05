package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.nitrite.annotation.FullTextIndex;
import io.micronaut.data.nitrite.annotation.SpatialIndex;
import java.util.UUID;

/**
 * Test entity for verifying index creation including compound, full-text, and spatial indexes.
 */
// tag::compound-index[]
@MappedEntity
@Index(name = "book_title_pages", columns = {"title", "pages"})
public class IndexedBook {
// end::compound-index[]
    @Id
    @GeneratedValue
    private String id;

    // tag::property-index[]
    @Index(columns = "title")
    private String title;
    // end::property-index[]

    private int pages;

    @FullTextIndex
    private String description;

    @SpatialIndex
    private String location;

    // UUID field without index - should NOT be indexed
    private UUID uuid;

    // UUID field with index - should be indexed
    @Index(columns = "indexedUuid")
    private UUID indexedUuid;

    public IndexedBook() {
    }

    public IndexedBook(String title, int pages) {
        this.title = title;
        this.pages = pages;
        this.uuid = UUID.randomUUID();
        this.indexedUuid = UUID.randomUUID();
    }

    /**
     * Gets the entity id.
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the entity id.
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Gets the title.
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Sets the title.
     * @param title the title to set
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Gets the number of pages.
     * @return the pages
     */
    public int getPages() {
        return pages;
    }

    /**
     * Sets the number of pages.
     * @param pages the pages to set
     */
    public void setPages(int pages) {
        this.pages = pages;
    }

    /**
     * Gets the description.
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description.
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the location.
     * @return the location
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the location.
     * @param location the location to set
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Gets the uuid.
     * @return the uuid
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Sets the uuid.
     * @param uuid the uuid to set
     */
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    /**
     * Gets the indexed uuid.
     * @return the indexed uuid
     */
    public UUID getIndexedUuid() {
        return indexedUuid;
    }

    /**
     * Sets the indexed uuid.
     * @param indexedUuid the indexed uuid to set
     */
    public void setIndexedUuid(UUID indexedUuid) {
        this.indexedUuid = indexedUuid;
    }
}
