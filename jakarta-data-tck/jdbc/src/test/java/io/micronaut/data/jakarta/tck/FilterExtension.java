package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ExpressionTests;
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
        if (testClass == ExpressionTests.class) {
            switch (testMethodName) {
                case "testPrependExpression" -> {
                    return ConditionEvaluationResult.disabled(
                        "Disabled until JDBC support for prepend expressions used by ExpressionTests.testPrependExpression is implemented"
                    );
                }

            }
        }
        return ConditionEvaluationResult.enabled(null);
    }

}
