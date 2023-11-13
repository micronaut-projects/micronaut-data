
package example

// tag::entity[]
// tag::entitywithprocedures[]
import jakarta.persistence.*

// end::entity[]
@NamedStoredProcedureQuery(name = "calculateSum",
        procedureName = "calculateSumInternal",
        parameters = [
            @StoredProcedureParameter(name = "productId", mode = ParameterMode.IN, type = Long.class),
            @StoredProcedureParameter(name = "result", mode = ParameterMode.OUT, type = Long.class)
        ]
)
// tag::entity[]
@Entity
class Product {

// end::entitywithprocedures[]
    @Id
    @GeneratedValue
    Long id
    String name
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    Manufacturer manufacturer

    Product(String name, Manufacturer manufacturer) {
        this.name = name
        this.manufacturer = manufacturer
    }

    Product() {
    }
}
// end::entity[]
