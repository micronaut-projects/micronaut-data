package io.micronaut.coherence.data.util;

public record EventRecord<T>(EventType eventType, T entity) {
}
