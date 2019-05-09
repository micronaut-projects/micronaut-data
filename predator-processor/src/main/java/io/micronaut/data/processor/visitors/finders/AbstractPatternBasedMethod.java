package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.query.Sort;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class AbstractPatternBasedMethod implements PredatorMethodCandidate {

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile("(.*)OrderBy([\\w\\d]+)");
    protected final Pattern pattern;

    public AbstractPatternBasedMethod(@Nonnull Pattern pattern) {
        this.pattern = pattern;
    }

    protected String matchOrder(String querySequence, List<Sort.Order> orders) {
        if (ORDER_BY_PATTERN.matcher(querySequence).matches()) {

            Matcher matcher = ORDER_BY_PATTERN.matcher(querySequence);
            StringBuffer buffer = new StringBuffer();
            while(matcher.find()) {
                matcher.appendReplacement(buffer, "$1");
                String orderDef = matcher.group(2);
                if (StringUtils.isNotEmpty(orderDef)) {
                    String prop = NameUtils.decapitalize(orderDef);
                    if (prop.endsWith("Desc")) {
                        orders.add(Sort.Order.desc(prop.substring(0, prop.length() - 4)));
                    } else if (prop.equalsIgnoreCase("Asc")) {
                        orders.add(Sort.Order.asc(prop.substring(0, prop.length() - 3)));
                    } else {
                        orders.add(Sort.Order.asc(prop));
                    }
                }
            }
            matcher.appendTail(buffer);
            return buffer.toString();
        }
        return querySequence;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        return pattern.matcher(methodElement.getName()).find();
    }

}
