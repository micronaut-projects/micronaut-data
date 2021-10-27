package io.micronaut.data.jdbc.h2.embedded.missing

import groovy.transform.EqualsAndHashCode
import io.micronaut.core.annotation.NonNull
import io.micronaut.data.jdbc.Marker

import javax.persistence.Column
import javax.persistence.Embeddable

@Marker
@Embeddable
@EqualsAndHashCode
class DiscountEO {

    @Column
    @NonNull
    BigDecimal value = BigDecimal.ZERO

}
