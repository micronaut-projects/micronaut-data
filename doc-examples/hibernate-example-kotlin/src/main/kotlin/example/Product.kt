
package example

// tag::entity[]
// tag::entitywithprocedures[]
import jakarta.persistence.*

// end::entity[]
@NamedStoredProcedureQuery(
    name = "calculateSum",
    procedureName = "calculateSumInternal",
    parameters = [StoredProcedureParameter(
        name = "productId",
        mode = ParameterMode.IN,
        type = Long::class
    ), StoredProcedureParameter(name = "result", mode = ParameterMode.OUT, type = Long::class)]
)
// tag::entity[]
@Entity
data class Product(

    // end::entitywithprocedures[]
    @Id
    @GeneratedValue
    var id: Long?,
    var name: String,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    var manufacturer: Manufacturer
)
// end::entity[]
