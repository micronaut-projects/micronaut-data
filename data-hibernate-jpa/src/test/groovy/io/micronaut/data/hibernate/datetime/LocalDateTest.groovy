package io.micronaut.data.hibernate.datetime

import io.micronaut.data.annotation.DateCreated

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import java.time.LocalDate

@Entity
class LocalDateTest {

    @Id
    @GeneratedValue
    Long id
    String name
    @DateCreated
    LocalDate createdDate
}
