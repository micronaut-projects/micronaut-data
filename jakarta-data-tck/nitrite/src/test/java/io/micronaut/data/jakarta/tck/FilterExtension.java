package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ConstraintTests;
import ee.jakarta.tck.data.standalone.entity.RestrictionTests;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;

public class FilterExtension implements ExecutionCondition {
    private static final ConditionEvaluationResult DISABLED = ConditionEvaluationResult.disabled("DISABLED");

    public FilterExtension() {
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Class<?> testClass = context.getTestClass().orElse(null);
        String testMethodName = context.getTestMethod().map(Method::getName).orElse("");
        if (testClass == ConstraintTests.class) {
            switch (testMethodName) {
                case "testLikeConstraintCustomWildcardsAndEscape",
                     "testNotLikeConstraintCustomWildcardsAndEscape" -> {
                    return DISABLED;
                }
            }
        }
        if (testClass == RestrictionTests.class) {
            switch (testMethodName) {
                case "testUnmatchable",
                     "testNotAllRestrictions",
                     "testNotAnyRestriction" -> {
                    return DISABLED;
                }
            }
        }
        return ConditionEvaluationResult.enabled(null);
    }

}
