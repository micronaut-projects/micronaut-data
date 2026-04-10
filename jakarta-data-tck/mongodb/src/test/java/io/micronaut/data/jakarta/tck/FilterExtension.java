package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ConstraintTests;
import ee.jakarta.tck.data.standalone.entity.EntityTests;
import ee.jakarta.tck.data.standalone.entity.ExpressionTests;
import ee.jakarta.tck.data.standalone.entity.JakartaQueryTests;
import ee.jakarta.tck.data.standalone.entity.RestrictionTests;
import ee.jakarta.tck.data.web.validation.ValidationTests;
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
        if (testClass == EntityTests.class) {
            switch (testMethodName) {
                case "testBasicRepositoryBuiltInMethods", "testBasicRepositoryMethods" -> {
                    return DISABLED; // Support deciding between persist or update when save is called
                }
                case "testIgnoreCase" -> {
                    return DISABLED; // Between doesn't support case insensitive
                }
                case "testCursoredPageOfNothing",
                     "testCursoredPageOf7FromCursor",
                     "testQueryWithOr",
                     "testFirstCursoredPageOf8AndNextPages",
                     "testCursoredPageWithoutTotalOfNothing",
                     "testFirstCursoredPageWithoutTotalOf6AndNextPages",
                     "testCursoredPageWithoutTotalOf9FromCursor" -> {
                    return DISABLED; // TODO
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
                    return DISABLED; // TODO
                }
            }
        }
        if (testClass == ValidationTests.class) {
            switch (testMethodName) {
                case "testSaveWithValidConstraints", "testUpdateAllWithValidConstraints",
                     "testUpdateWithValidConstraints" -> {
                    return DISABLED; // Support deciding between persist or update when save is called
                }

            }
        }
        if (testClass == JakartaQueryTests.class) {
            switch (testMethodName) {
                case "shouldAnd" -> {
                    return DISABLED; // Support deciding between persist or update when saveAll is called
                }
            }
        }
        if (testClass == ConstraintTests.class) {
            return DISABLED; // TODO
        }
        if (testClass == RestrictionTests.class) {
            switch (testMethodName) {
                case "testUnmatchable" -> {
                    return DISABLED; // TODO
                }
            }
        }
        return ConditionEvaluationResult.enabled(null);
    }

}
