
package example

import jakarta.persistence.*

@NamedStoredProcedureQuery(name = "calculateSum",
        procedureName = "calculateSumInternal",
        parameters = [
            @StoredProcedureParameter(name = "productId", mode = ParameterMode.IN, type = Long.class),
            @StoredProcedureParameter(name = "result", mode = ParameterMode.OUT, type = Long.class)
        ]
)
@Entity
class Product {

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
