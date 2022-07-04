package io.micronaut.data.runtime.criteria.ext

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
class OtherEntity(
        @field:Id
        @field:GeneratedValue(strategy = GenerationType.IDENTITY)
        var id: Long? = null,
        var enabled: Boolean? = null,
        var age: Long? = null,
        var amount: BigDecimal? = null,
        var budget: BigDecimal? = null,
        val name: String,
        @ManyToOne
        val test: TestEntity)
