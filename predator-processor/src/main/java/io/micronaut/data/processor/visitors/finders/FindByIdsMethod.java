package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.regex.Pattern;

/**
 * Supports a method called findByIds.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class FindByIdsMethod extends AbstractPatternBasedMethod {

    @Override
    public int getOrder() {
        return FindByFinder.DEFAULT_POSITION - 200;
    }

    public FindByIdsMethod() {
        super(Pattern.compile("^(find|search|get|list)ByIds$"));
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext)
                && TypeUtils.isIterableOfEntity(matchContext.getReturnType()) &&
                areParametersValid(matchContext);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        SourcePersistentEntity rootEntity = matchContext.getRootEntity();
        SourcePersistentProperty identity = rootEntity.getIdentity();
        if (identity != null) {

            Query query = Query.from(rootEntity);
            query.inList(identity.getName(), new QueryParameter(matchContext.getParameters()[0].getName()));

            return new MethodMatchInfo(
                    matchContext.getReturnType(),
                    query,
                    FindAllInterceptor.class);
        } else {
            matchContext.fail("Cannot query by ID on entity that defines no ID");
            return null;
        }
    }

    private boolean areParametersValid(@NonNull MatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getParameters();
        return parameters.length == 1 && (parameters[0].isArray() || (parameters[0].getType() != null && parameters[0].getType().isAssignable(Iterable.class)));
    }
}
