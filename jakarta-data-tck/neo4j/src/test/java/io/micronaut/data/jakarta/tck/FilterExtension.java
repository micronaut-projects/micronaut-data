package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ConstraintTests;
import ee.jakarta.tck.data.standalone.entity.EntityTests;
import ee.jakarta.tck.data.standalone.entity.ExpressionTests;
import ee.jakarta.tck.data.standalone.entity.JakartaEventBuiltInRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.JakartaEventCustomRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.RestrictionTests;
import ee.jakarta.tck.data.standalone.entity.SortByExpressionTests;
import ee.jakarta.tck.data.standalone.persistence.stateful.StatefulPersistenceEntityTests;
import ee.jakarta.tck.data.standalone.persistence.stateless.NativeQueryTests;
import ee.jakarta.tck.data.standalone.persistence.stateless.PersistenceEntityTests;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Method;
import java.util.Map;
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

    // Tests that read or query the capital of a Country. Property graphs have no embedded objects, so TestClassVisitor
    // makes the embedded City transient and gives the finders on it a query that no test runs. The other tests of these
    // classes run against the rest of the entity
    private static final Map<Class<?>, Set<String>> READS_COUNTRY_CAPITAL = Map.of(
        ConstraintTests.class, Set.of(
            "testEqualToConstraint",
            "testGreaterThanConstraint",
            "testLikeConstraintCustomWildcards",
            "testLikeConstraintCustomWildcardsAndEscape",
            "testLikeConstraintLiteral",
            "testLikeConstraintPattern",
            "testLikeConstraintPrefix",
            "testLikeConstraintSubstring",
            "testLikeConstraintSuffix",
            "testSortByEmbeddableAttribute"
        ),
        RestrictionTests.class, Set.of(
            "testAllOf1Restriction",
            "testAnyOf1Restriction",
            "testAnyOf2Restrictions",
            "testEqualToRestriction",
            "testInRestriction"
        ),
        SortByExpressionTests.class, Set.of(
            "testSortByNavigableExpression"
        )
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
        if (READS_COUNTRY_CAPITAL.getOrDefault(testClass, Set.of()).contains(testMethodName)) {
            return ConditionEvaluationResult.disabled("Country embeds City; graph repositories do not support embedded properties");
        }
        if (testClass == ExpressionTests.class && testMethodName.equals("testPrependExpression")) {
            // The Restriction converter calls in() on the prepended expression, and IExpression.in() throws before a query is built
            return ConditionEvaluationResult.disabled("IExpression.in() is not supported on a function expression");
        }
        if (testClass == EntityTests.class && CURSORED_PAGE_TESTS.contains(testMethodName)) {
            // Keyset pagination is implemented by the SQL runtime only, and the graph runtime rejects a CursoredPage
            // method. Each of these pages through one, testQueryWithOr included: it pages a JCQL query with OR
            return ConditionEvaluationResult.disabled("Graph repositories do not implement cursored pagination");
        }
        return ConditionEvaluationResult.enabled(null);
    }

}
