package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record PersonWithIdAndNameDto(Long id, String name) {
}
