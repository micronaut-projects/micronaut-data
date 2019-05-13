package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.intercept.UpdateInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.data.model.query.QueryParameter;
import io.micronaut.data.processor.model.SourcePersistentEntity;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Support for simple updates by ID.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class UpdateMethod extends AbstractPatternBasedMethod {

    /**
     * Default constructor.
     */
    public UpdateMethod() {
        super(Pattern.compile("^update\\w*$"));
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement, MatchContext matchContext) {
        return super.isMethodMatch(methodElement, matchContext) &&
                methodElement.getParameters().length > 1 &&
                isValidReturnType(methodElement.getReturnType()) &&
                hasIdParameter(methodElement.getParameters());
    }

    private boolean isValidReturnType(ClassElement returnType) {
        if (returnType != null) {
            return returnType.getName().equals("void");
        }
        return false;
    }

    private boolean hasIdParameter(ParameterElement[] parameters) {

        return Arrays.stream(parameters).anyMatch(p ->
                p.hasAnnotation(Id.class)
        );
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(
            @Nonnull MethodMatchContext matchContext) {
        ParameterElement[] parameters = matchContext.getMethodElement().getParameters();
        List<ParameterElement> remainingParameters = Arrays.stream(parameters)
                .filter(p -> !p.hasAnnotation(Id.class))
                .collect(Collectors.toList());

        ParameterElement idParameter = Arrays.stream(parameters).filter(p -> p.hasAnnotation(Id.class)).findFirst()
                .orElse(null);
        if (idParameter == null) {
            matchContext.fail("ID required for update method, but not specified");
            return null;
        }

        SourcePersistentEntity entity = matchContext.getRootEntity();

        SourcePersistentProperty identity = entity.getIdentity();
        if (identity == null) {
            matchContext.fail("Cannot update by ID for entity that has no ID");
            return null;
        } else {
            ClassElement idType = identity.getType();
            ClassElement idParameterType = idParameter.getType();
            if (idType == null || idParameterType == null) {
                matchContext.fail("Unsupported ID type");
                return null;
            } else if (idType.equals(idParameterType)) {
                matchContext.fail("ID type of method [" + idParameterType.getName() + "] does not match ID type of entity: " + idType.getName());
            }
        }


        Query query = Query.from(entity);
        query.idEq(new QueryParameter(idParameter.getName()));
        List<String> properiesToUpdate = new ArrayList<>(remainingParameters.size());
        for (ParameterElement parameter : remainingParameters) {
            String name = parameter.getName();
            SourcePersistentProperty prop = entity.getPropertyByName(name);
            if (prop == null) {
                matchContext.fail("Cannot update non-existent property" + name);
                return null;
            } else {
                if (prop.isGenerated()) {
                    matchContext.fail("Cannot update a generated property" + name);
                    return null;
                } else {
                    properiesToUpdate.add(name);
                }
            }
        }

        Element element = matchContext.getParametersInRole().get(TypeRole.LAST_UPDATED_PROPERTY);
        if (element instanceof PropertyElement) {
            properiesToUpdate.add(element.getName());
        }

        MethodMatchInfo info = new MethodMatchInfo(
                matchContext.getReturnType(),
                query,
                UpdateInterceptor.class,
                MethodMatchInfo.OperationType.UPDATE,
                properiesToUpdate.toArray(new String[0])
        );

        info.addParameterRole(
                TypeRole.ID,
                idParameter.getName()
        );
        return info;
    }
}
