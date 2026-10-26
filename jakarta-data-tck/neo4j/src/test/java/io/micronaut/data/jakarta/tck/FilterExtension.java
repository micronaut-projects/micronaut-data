package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ConstraintTests;
import ee.jakarta.tck.data.standalone.entity.EntityTests;
import ee.jakarta.tck.data.standalone.entity.ExpressionTests;
import ee.jakarta.tck.data.standalone.entity.FirstTests;
import ee.jakarta.tck.data.standalone.entity.LimitTests;
import ee.jakarta.tck.data.standalone.entity.JakartaEventBuiltInRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.JakartaEventCustomRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.RestrictionTests;
import ee.jakarta.tck.data.standalone.entity.SortByExpressionTests;
import ee.jakarta.tck.data.standalone.entity.SortNullableTests;
import ee.jakarta.tck.data.standalone.persistence.stateful.StatefulPersistenceEntityTests;
import ee.jakarta.tck.data.standalone.persistence.stateless.NativeQueryTests;
import ee.jakarta.tck.data.standalone.persistence.stateless.PersistenceEntityTests;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.Set;

public class FilterExtension implements ExecutionCondition {
    private static final Set<String> CURSORED_PAGE_TESTS = Set.of(
        "testCursoredPageOfNothing",
        "testCursoredPageOf7FromCursor",
        "testQueryWithOr",
        "testFirstCursoredPageOf8AndNextPages",
        "testCursoredPageWithoutTotalOfNothing",
        "testFirstCursoredPageWithoutTotalOf6AndNextPages",
        "testCursoredPageWithoutTotalOf9FromCursor"
    );

    private static final Set<Class<?>> DEPLOYS_COUNTRY = Set.of(
        ConstraintTests.class,
        ExpressionTests.class,
        FirstTests.class,
        LimitTests.class,
        RestrictionTests.class,
        SortByExpressionTests.class,
        SortNullableTests.class
    );

    public FilterExtension() {
        // Registered through META-INF/services, so JUnit instantiates it reflectively
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
        if (DEPLOYS_COUNTRY.contains(testClass)) {
            // These deploy the Countries repository, whose Country entity embeds its capital City. Property graphs
            // have no embedded objects: a node holds scalar properties, and the Cypher builder rejects @Embedded
            return ConditionEvaluationResult.disabled("Country embeds City; graph repositories do not support embedded properties");
        }
        if (testClass == EntityTests.class && CURSORED_PAGE_TESTS.contains(testMethodName)) {
            // Keyset pagination is implemented by the SQL runtime only: the graph runtime answers a CursoredPage
            // method with an offset Page
            return ConditionEvaluationResult.disabled("Graph repositories do not implement cursored pagination");
        }
        return ConditionEvaluationResult.enabled(null);
    }

}
