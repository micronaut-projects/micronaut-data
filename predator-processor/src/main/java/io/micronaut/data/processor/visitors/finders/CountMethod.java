package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.intercept.CountAllInterceptor;
import io.micronaut.data.intercept.CountByInterceptor;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

public class CountMethod extends AbstractListMethod {
    private static final String METHOD_PATTERN = "^((count)(\\S*?))$";

    public CountMethod() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && doesReturnNumber(methodElement);
    }

    @Nonnull
    @Override
    protected PredatorMethodInfo buildInfo(@Nullable Query query) {
        if (query != null) {
            query.projections().count();
            return new PredatorMethodInfo(
                    query,
                    CountByInterceptor.class
            );
        } else {
            return new PredatorMethodInfo(
                    null,
                    CountAllInterceptor.class
            );
        }
    }
}
