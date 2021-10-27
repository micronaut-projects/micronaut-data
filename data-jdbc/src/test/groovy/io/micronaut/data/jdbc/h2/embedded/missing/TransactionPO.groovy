package io.micronaut.data.jdbc.h2.embedded.missing

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.NonNull

import javax.persistence.Column
import javax.persistence.Embedded
import javax.persistence.Id
import javax.persistence.MappedSuperclass
import javax.validation.constraints.NotNull

@CompileStatic
@MappedSuperclass
abstract class TransactionPO {

    @Id
    @Column(nullable = false, updatable = false)
    @NotNull
    private String id

    @NonNull
    @Embedded
    DiscountEO discount = new DiscountEO(value: BigDecimal.ZERO)

}
