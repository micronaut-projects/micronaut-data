package io.micronaut.data.model.finders;

import java.util.regex.Pattern;

abstract class AbstractFindByFinder extends DynamicFinder {
    public static final String[] OPERATORS = { OPERATOR_AND, OPERATOR_OR };

    protected AbstractFindByFinder(Pattern pattern) {
        super(pattern, OPERATORS);
    }
}
