package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record BookWithIdAndTitle(Long id, String title) {
}

