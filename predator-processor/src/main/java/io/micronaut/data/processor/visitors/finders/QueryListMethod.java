package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindAllByInterceptor;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryListMethod extends ListMethod {

    public static final int POSITION = DEFAULT_POSITION - 200;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(":([a-zA-Z0-9]+)");

    @Override
    public int getOrder() {
        // give higher chance to match annotation
        return POSITION;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return methodElement.getValue(Query.class, String.class).map(StringUtils::isNotEmpty).orElse(false) && super.isMethodMatch(methodElement);
    }

    @Nullable
    @Override
    public PredatorMethodInfo buildMatchInfo(@Nonnull MethodMatchContext matchContext) {
        MethodElement methodElement = matchContext.getMethodElement();
        String queryString = methodElement.getValue(Query.class, String.class).orElseThrow(() ->
            new IllegalStateException("Should only be called if Query has value!")
        );
        Matcher matcher = VARIABLE_PATTERN.matcher(queryString);
        List<ParameterElement> parameters = Arrays.asList(matchContext.getParameters());
        Map<String, String> parameterBinding = new LinkedHashMap<>(parameters.size());
        while (matcher.find()) {
            String name = matcher.group(1);
            Optional<ParameterElement> element = parameters.stream().filter(p -> p.getName().equals(name)).findFirst();
            if (element.isPresent()) {
                parameterBinding.put(name, element.get().getName());
            } else {
                matchContext.getVisitorContext().fail(
                        "No method parameter found for name :" + name,
                        methodElement
                );
                return null;
            }
        }

        return new PredatorMethodInfo(
            new RawQuery(matchContext.getEntity(), parameterBinding),
            FindAllByInterceptor.class
        );
    }
}
