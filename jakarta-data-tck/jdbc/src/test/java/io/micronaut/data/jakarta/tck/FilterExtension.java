package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.EntityTests;
import ee.jakarta.tck.data.standalone.entity.ExpressionTests;
import ee.jakarta.tck.data.standalone.entity.JakartaQueryTests;
import ee.jakarta.tck.data.standalone.persistence.PersistenceEntityTests;
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
                case "testBasicRepositoryBuiltInMethods",  "testBasicRepositoryMethods" -> {
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
        if (testClass == ValidationTests.class) {
            switch (testMethodName) {
                case "testSaveWithValidConstraints",  "testUpdateAllWithValidConstraints", "testUpdateWithValidConstraints" -> {
                    return DISABLED; // Support deciding between persist or update when save is called
                }

            }
        }
        if (testClass == ExpressionTests.class) {
            switch (testMethodName) {
                case "testPrependExpression" -> {
                    return DISABLED; // TODO
                }

            }
        }
        return ConditionEvaluationResult.enabled(null);
    }

}
