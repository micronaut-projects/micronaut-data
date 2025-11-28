package io.micronaut.data.tck.entities;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record TrainNameModelDto(String name, String model) {
}
