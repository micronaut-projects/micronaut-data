package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.intercept.ExistsByInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import java.util.regex.Pattern;

public class ExistsByFinder extends AbstractFindByFinder {

    private static final String METHOD_PATTERN = "((exists)(\\S*?)By)([A-Z]\\w*)";

    public ExistsByFinder() {
        super(Pattern.compile(METHOD_PATTERN));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnBoolean(methodElement);
    }

    @Nullable
    @Override
    protected PredatorMethodInfo buildInfo(
            @NonNull MethodMatchContext matchContext,
            ClassElement queryResultType, @Nullable Query query) {
        if (query != null) {
            query.projections().id();
        }
        return new PredatorMethodInfo(
                matchContext.getReturnType(),
                query,
                ExistsByInterceptor.class
        );
    }

}
