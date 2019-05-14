package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Dynamic finder for support for delete operations.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DeleteByMethod extends DynamicFinder {

    protected static final String[] PREFIXES = {"delete", "remove", "erase", "eliminate"};

    /**
     * Default constructor.
     */
    public DeleteByMethod() {
        super(PREFIXES);
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) && TypeUtils.doesReturnVoid(methodElement); // void return
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(
            MethodMatchContext matchContext, ClassElement queryResultType, @Nullable Query query) {
        if (query == null) {
            matchContext.fail("Unable to implement delete method with no query arguments");
            return null;
        } else {
            return new MethodMatchInfo(
                    null,
                    query,
                    DeleteAllInterceptor.class,
                    MethodMatchInfo.OperationType.DELETE
            );
        }
    }
}
