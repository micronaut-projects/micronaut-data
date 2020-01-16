package io.micronaut.data.spring.runtime.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.runtime.config.DataConfiguration;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import javax.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring version of pageable binder.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(classes = {RequestArgumentBinder.class, Pageable.class})
@Singleton
public class SpringPageableRequestArgumentBinder implements TypedRequestArgumentBinder<Pageable> {

    public static final Argument<Pageable> TYPE = Argument.of(Pageable.class);

    private final DataConfiguration.PageableConfiguration configuration;
    private final Function<String, Sort.Order> sortMapper;

    /**
     * Default constructor.
     * @param configuration The configuration
     */
    protected SpringPageableRequestArgumentBinder(DataConfiguration.PageableConfiguration configuration) {
        this.configuration = configuration;
        sortMapper = s -> {
            String[] tokens = configuration.getSortDelimiterPattern().split(s);
            if (tokens.length == 1) {
                return new Sort.Order(Sort.Direction.ASC, tokens[0]);
            } else {
                try {

                    Sort.Direction direction = Sort.Direction.valueOf(tokens[1].toUpperCase(Locale.ENGLISH));
                    return new Sort.Order(direction, tokens[0]);
                } catch (IllegalArgumentException e) {
                    return new Sort.Order(Sort.Direction.ASC, tokens[0]);
                }
            }
        };
    }

    @Override
    public Argument<Pageable> argumentType() {
        return TYPE;
    }

    @Override
    public BindingResult<Pageable> bind(ArgumentConversionContext<Pageable> context, HttpRequest<?> source) {
        HttpParameters parameters = source.getParameters();
        int page = Math.max(parameters.getFirst(configuration.getPageParameterName(), Integer.class)
                .orElse(0), 0);
        final int configuredMaxSize = configuration.getMaxPageSize();
        final int defaultSize = configuration.getDefaultPageSize();
        int size = Math.min(parameters.getFirst(configuration.getSizeParameterName(), Integer.class)
                .orElse(defaultSize), configuredMaxSize);
        String sortParameterName = configuration.getSortParameterName();
        boolean hasSort = parameters.contains(sortParameterName);
        Pageable pageable;
        Sort sort;
        if (hasSort) {
            List<String> sortParams = parameters.getAll(sortParameterName);

            List<Sort.Order> orders = sortParams.stream()
                    .map(sortMapper)
                    .collect(Collectors.toList());
            sort = Sort.by(orders);
        } else {
            sort = Sort.unsorted();
        }

        if (size < 1) {
            if (page == 0 && configuredMaxSize < 1 && sort.isUnsorted()) {
                pageable = Pageable.unpaged();
            } else {
                pageable = PageRequest.of(page, defaultSize, sort);
            }
        } else {
            pageable = PageRequest.of(page, size, sort);
        }

        return () -> Optional.of(pageable);
    }
}
