package example;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;

// tag::unique-index[]
@MappedEntity
public class CatalogItem {
    @Id
    @GeneratedValue
    private String id;

    @Index(columns = "sku", unique = true)
    private String sku;

    private String name;

    public CatalogItem() {
    }

    public CatalogItem(String sku, String name) {
        this.sku = sku;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
// end::unique-index[]
