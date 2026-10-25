package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.JakartaEventBuiltInRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.JakartaEventCustomRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.JakartaQueryUpdateTests;
import ee.jakarta.tck.data.standalone.persistence.stateful.StatefulPersistenceEntityTests;
import ee.jakarta.tck.data.standalone.persistence.stateless.NativeQueryTests;
import ee.jakarta.tck.data.standalone.persistence.stateless.PersistenceEntityTests;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * The subset of the Jakarta Data TCK that applies to Elasticsearch: the tests this filter disables
 * need features a document search store does not offer.
 */
public class FilterExtension implements ExecutionCondition {

    private static final Map<Class<?>, String> DISABLED_CLASSES = Map.of(
        StatefulPersistenceEntityTests.class, "Micronaut Data does not implement Jakarta Data stateful repositories",
        // Both deploy the Catalog repository, which uses jakarta.persistence.query.NativeQuery from Jakarta Persistence 4.0
        NativeQueryTests.class, "jakarta.persistence.query.NativeQuery requires Jakarta Persistence 4.0",
        PersistenceEntityTests.class, "jakarta.persistence.query.NativeQuery requires Jakarta Persistence 4.0",
        // Micronaut Data delivers lifecycle events to ApplicationEventListener beans rather than CDI observers
        JakartaEventBuiltInRepositoryTest.class, "Jakarta Data lifecycle events are observed via CDI",
        JakartaEventCustomRepositoryTest.class, "Jakarta Data lifecycle events are observed via CDI",
        JakartaQueryUpdateTests.class, "Update queries are not supported by document search stores"
    );

    private static final Map<Class<?>, Set<String>> DISABLED_METHODS = Map.of();

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Class<?> testClass = context.getTestClass().orElse(null);
        if (testClass == null) {
            return ConditionEvaluationResult.enabled("");
        }
        for (Map.Entry<Class<?>, String> disabled : DISABLED_CLASSES.entrySet()) {
            if (disabled.getKey().isAssignableFrom(testClass) || testClass.getEnclosingClass() == disabled.getKey()) {
                return ConditionEvaluationResult.disabled(disabled.getValue());
            }
        }
        String method = context.getTestMethod().map(Method::getName).orElse("");
        if (DISABLED_METHODS.getOrDefault(testClass, Set.of()).contains(method)) {
            return ConditionEvaluationResult.disabled("Not supported by document search stores");
        }
        return ConditionEvaluationResult.enabled("");
    }
}
