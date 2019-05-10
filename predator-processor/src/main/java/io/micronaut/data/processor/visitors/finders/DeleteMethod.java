package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.intercept.DeleteAllInterceptor;
import io.micronaut.data.intercept.DeleteOneInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Support for simple delete operations.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class DeleteMethod extends AbstractListMethod {

    /**
     * Default constructor.
     */
    public DeleteMethod() {
        super("delete", "remove", "erase", "eliminate");
    }

    @Override
    public final int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return super.isMethodMatch(methodElement) && TypeUtils.doesReturnVoid(methodElement); // void return
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        if (parameters.length == 1) {
            ClassElement genericType = parameters[0].getGenericType();
            if (genericType != null) {
                if (genericType.isAssignable(matchContext.getRootEntity().getName())) {

                    return new MethodMatchInfo(
                            null,
                            null,
                            DeleteOneInterceptor.class
                    );
                } else if(TypeUtils.isIterableOfEntity(genericType)) {
                    return new MethodMatchInfo(
                            null,
                            null,
                            DeleteAllInterceptor.class
                    );
                }
            }
        }
        return super.buildMatchInfo(matchContext);
    }

    @Nullable
    @Override
    protected MethodMatchInfo buildInfo(@NonNull MethodMatchContext matchContext, @NonNull ClassElement queryResultType, @Nullable Query query) {
        if (query != null) {
            return new MethodMatchInfo(
                    null,
                    query,
                    DeleteAllInterceptor.class
            );
        } else {
            return new MethodMatchInfo(
                    null,
                    null,
                    DeleteAllInterceptor.class
            );
        }
    }

 }
