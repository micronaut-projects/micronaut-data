
package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty

// tag::book[]
@MappedEntity
class Book {
    @Id
    @GeneratedValue
    private String id
    private String title
    private int pages
    @MappedProperty(converter = ItemPriceAttributeConverter)
    @Nullable
    private ItemPrice itemPrice
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

    ItemPrice getItemPrice() {
        return itemPrice
    }

    void setItemPrice(ItemPrice itemPrice) {
        this.itemPrice = itemPrice
    }
// tag::book[]
    //...
}
// end::book[]
