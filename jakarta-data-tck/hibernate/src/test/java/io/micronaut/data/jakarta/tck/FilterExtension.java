package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ConstraintTests;
import ee.jakarta.tck.data.standalone.entity.EntityTests;
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
        if (testClass == PersistenceEntityTests.class) {
            switch (testMethodName) {
                case "testMultipleInsertUpdateDelete",
                     "testVersionedInsertUpdateDelete" -> {
                    return DISABLED; // Optimistic locking
                }
            }
        }
        if (testClass == EntityTests.class) {
            switch (testMethodName) {
                case "testLiteralTrue" -> {
                    return DISABLED; // https://hibernate.atlassian.net/browse/HHH-19177
                }
            }
        }
        if (testClass == ConstraintTests.class) {
            switch (testMethodName) {
                case "testGreaterThanConstraint",  "testNotLikeConstraintCustomWildcardsAndEscape" -> {
                    return DISABLED; // https://github.com/jakartaee/data/issues/1364
                }

            }
        }
        return ConditionEvaluationResult.enabled(null);
    }

}
