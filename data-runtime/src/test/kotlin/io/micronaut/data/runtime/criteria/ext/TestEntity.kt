package io.micronaut.data.runtime.criteria.ext

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

@Entity
class TestEntity(
        @field:Id
        @field:GeneratedValue(strategy = GenerationType.IDENTITY)
        var id: Long? = null,
        var enabled: Boolean? = false,
        var age: Long? = null,
        var amount: BigDecimal? = null,
        var budget: BigDecimal? = null,
        var birth: LocalDate = LocalDate.now(),

        //    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "test")
        @OneToMany(mappedBy = "test")
        var others: List<OtherEntity>? = null,
        val name: String?,
        val description: String
        )
