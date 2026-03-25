package io.micronaut.data.processor.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "other_table")
@Table(name = "other_table")
public record OtherEntity(
        @Id
        @GeneratedValue
        Long id,
        String someColumn) {
}
