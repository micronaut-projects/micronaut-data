
package example

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.NamedStoredProcedureQuery
import jakarta.persistence.ParameterMode
import jakarta.persistence.StoredProcedureParameter

@NamedStoredProcedureQuery(
    name = "calculateSum",
    procedureName = "calculateSumInternal",
    parameters = [StoredProcedureParameter(
        name = "productId",
        mode = ParameterMode.IN,
        type = Long::class
    ), StoredProcedureParameter(name = "result", mode = ParameterMode.OUT, type = Long::class)]
)
@Entity
data class Product(
    @Id
    @GeneratedValue
    var id: Long?,
    var name: String,
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    var manufacturer: Manufacturer
)
