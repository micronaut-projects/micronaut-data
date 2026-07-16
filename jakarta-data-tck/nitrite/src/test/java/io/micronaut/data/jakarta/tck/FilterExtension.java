package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ConstraintTests;
import ee.jakarta.tck.data.standalone.entity.ExpressionTests;
import ee.jakarta.tck.data.standalone.entity.JakartaQueryTests;
import ee.jakarta.tck.data.standalone.entity.RestrictionTests;
import ee.jakarta.tck.data.standalone.persistence.PersistenceEntityTests;
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
        if (testClass == PersistenceEntityTests.class) {
            switch (testMethodName) {
                case "testInsertEntityThatAlreadyExists",
                     "testIdAttributeWithDifferentName" -> {
                    return DISABLED;
                }
            }
        }
        if (testClass == JakartaQueryTests.class) {
            switch (testMethodName) {
                case "shouldReturnIdUsingIdFunctionOrderById",
                     "shouldReturnNameAndQuantity" -> {
                    return DISABLED;
                }
            }
        }
        if (testClass == ExpressionTests.class) {
            switch (testMethodName) {
                case "testPrependValue",
                     "testLeft1",
                     "testLeft3",
                     "testLower",
                     "testUpper",
                     "testLength2",
                     "testLength5",
                     "testAppendExpression",
                     "testCastToDouble",
                     "testPrependExpression",
                     "testRight1",
                     "testRight3",
                     "testAppendValue" -> {
                    return DISABLED;
                }
            }
        }
        if (testClass == ConstraintTests.class) {
            switch (testMethodName) {
                case "testAtMostConstraint",
                     "testLikeConstraintCustomWildcardsAndEscape",
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
