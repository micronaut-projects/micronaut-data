package example;

import javax.persistence.*;

@Entity
public class Product {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @ManyToOne
    private Manufacturer manufacturer;

    public Product(String name, Manufacturer manufacturer) {
        this.name = name;
        this.manufacturer = manufacturer;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Manufacturer getManufacturer() {
        return manufacturer;
    }
}
