package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.intercept.DeleteByInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nullable;

public class DeleteByMethod extends DynamicFinder {

    protected static final String[] PREFIXES = {"delete", "remove", "erase", "eliminate"};

    public DeleteByMethod() {
        super(PREFIXES);
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnVoid(methodElement); // void return
    }

    @Nullable
    @Override
    protected PredatorMethodInfo buildInfo(
            MethodMatchContext matchContext, ClassElement queryResultType, @Nullable Query query) {
        if (query == null) {
            matchContext.fail("Unable to implement delete method with no query arguments");
            return null;
        } else {
            return new PredatorMethodInfo(
                    null,
                    query,
                    DeleteByInterceptor.class,
                    PredatorMethodInfo.OperationType.DELETE
            );
        }
    }
}
