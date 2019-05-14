package io.micronaut.data.runtime.spring;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.TypeConverter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Type converters for supporting classes and interfaces in the {@link org.springframework.data.domain} package.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Factory
@Internal
public class SpringDataTypeConverters {

    @Singleton
    TypeConverter<io.micronaut.data.model.Page, Page> pageConverter() {
        return (object, targetType, context) -> Optional.of(new PageDelegate(object));
    }

    @Singleton
    TypeConverter<Pageable, io.micronaut.data.model.Pageable> pageableConverter() {
        return (object, targetType, context) -> Optional.of(new PageableDelegate(object));
    }

    @Singleton
    TypeConverter<Sort, io.micronaut.data.model.query.Sort> sortConverter() {
        return (object, targetType, context) -> Optional.of(new SortDelegate(object));
    }
}
