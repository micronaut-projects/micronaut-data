package io.micronaut.data.runtime.date;

public interface DateTimeProvider<T> {

    T getNow();
}
