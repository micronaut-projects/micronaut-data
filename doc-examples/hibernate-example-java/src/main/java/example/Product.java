
package example;

// tag::entity[]
// tag::entitywithprocedures[]
import jakarta.persistence.*;

// end::entity[]
@NamedStoredProcedureQuery(name = "calculateSum",
    procedureName = "calculateSumInternal",
    parameters = {
        @StoredProcedureParameter(name = "productId", mode = ParameterMode.IN, type = Long.class),
        @StoredProcedureParameter(name = "result", mode = ParameterMode.OUT, type = Long.class)
    }
)
// tag::entity[]
@Entity
class Product {

    // end::entitywithprocedures[]
    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Manufacturer manufacturer;

    public Product(String name, Manufacturer manufacturer) {
        this.name = name;
        this.manufacturer = manufacturer;
    }

    public Product() {
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

    public void setName(String name) {
        this.name = name;
    }

    public Manufacturer getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(Manufacturer manufacturer) {
        this.manufacturer = manufacturer;
    }
}
