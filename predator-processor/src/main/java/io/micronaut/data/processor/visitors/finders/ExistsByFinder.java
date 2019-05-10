package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.intercept.ExistsByInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

/**
 * Dynamic finder for exists queries.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ExistsByFinder extends DynamicFinder {

    /**
     * The prefixes used
     */
    public static final String[] PREFIXES = new String[] { "exists" };

    /**
     * Default constructor.
     */
    public ExistsByFinder() {
        super(PREFIXES);
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnBoolean(methodElement);
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(
            @NonNull MethodMatchContext matchContext,
            @NonNull ClassElement queryResultType,
            @Nullable Query query) {
        if (query != null) {
            query.projections().id();
        }
        return new MethodMatchInfo(
                matchContext.getReturnType(),
                query,
                ExistsByInterceptor.class
        );
    }

}
