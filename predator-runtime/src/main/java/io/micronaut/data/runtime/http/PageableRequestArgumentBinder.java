package io.micronaut.data.runtime.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.runtime.config.PredatorConfiguration;
import io.micronaut.http.HttpParameters;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * A request argument binder for binding a {@link Pageable} object from the request.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Requires(classes = RequestArgumentBinder.class)
@Singleton
public class PageableRequestArgumentBinder implements TypedRequestArgumentBinder<Pageable> {

    public static final Argument<Pageable> TYPE = Argument.of(Pageable.class);

    private final PredatorConfiguration.PageableConfiguration configuration;

    /**
     * Default constructor.
     * @param configuration The configuration
     */
    protected PageableRequestArgumentBinder(PredatorConfiguration.PageableConfiguration configuration) {
        this.configuration = configuration;
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
        int size = Math.min(parameters.getFirst(configuration.getSizeParameterName(), Integer.class)
                       .orElse(configuredMaxSize), configuredMaxSize);

        return () -> {
            if (size < 1) {
                if (page == 0 && configuredMaxSize < 1) {
                    return Optional.of(Pageable.UNPAGED);
                } else {
                    return Optional.of(Pageable.from(page, configuredMaxSize));
                }
            } else {
                return Optional.of(Pageable.from(page, size));
            }
        };
    }
}
