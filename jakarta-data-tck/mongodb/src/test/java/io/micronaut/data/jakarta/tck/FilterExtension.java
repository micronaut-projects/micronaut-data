package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ConstraintTests;
import ee.jakarta.tck.data.standalone.entity.EntityTests;
import ee.jakarta.tck.data.standalone.entity.ExpressionTests;
import ee.jakarta.tck.data.standalone.entity.JakartaEventBuiltInRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.JakartaEventCustomRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.RestrictionTests;
import ee.jakarta.tck.data.standalone.persistence.stateful.StatefulPersistenceEntityTests;
import ee.jakarta.tck.data.standalone.persistence.stateless.NativeQueryTests;
import ee.jakarta.tck.data.standalone.persistence.stateless.PersistenceEntityTests;
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
        if (testClass == StatefulPersistenceEntityTests.class) {
            // Micronaut Data does not implement Jakarta Data stateful repositories
            return ConditionEvaluationResult.disabled("Stateful repositories are not supported");
        }
        if (testClass == NativeQueryTests.class || testClass == PersistenceEntityTests.class) {
            // Both deploy the Catalog repository, which annotates methods with jakarta.persistence.query.NativeQuery,
            // an annotation that only exists in Jakarta Persistence 4.0
            return ConditionEvaluationResult.disabled("jakarta.persistence.query.NativeQuery requires Jakarta Persistence 4.0");
        }
        if (testClass == JakartaEventBuiltInRepositoryTest.class || testClass == JakartaEventCustomRepositoryTest.class) {
            // The TCK observes Jakarta Data lifecycle events through CDI. Micronaut Data delivers them to
            // ApplicationEventListener beans instead, and does not emit the upsert events these tests expect.
            return ConditionEvaluationResult.disabled("Jakarta Data lifecycle events are observed via CDI");
        }
        if (testClass == EntityTests.class) {
            switch (testMethodName) {
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
