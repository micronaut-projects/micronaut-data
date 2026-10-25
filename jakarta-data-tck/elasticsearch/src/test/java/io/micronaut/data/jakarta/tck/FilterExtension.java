package io.micronaut.data.jakarta.tck;

import ee.jakarta.tck.data.standalone.entity.ConstraintTests;
import ee.jakarta.tck.data.standalone.entity.EntityTests;
import ee.jakarta.tck.data.standalone.entity.ExpressionTests;
import ee.jakarta.tck.data.standalone.entity.JakartaEventBuiltInRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.JakartaEventCustomRepositoryTest;
import ee.jakarta.tck.data.standalone.entity.JakartaQueryTests;
import ee.jakarta.tck.data.standalone.entity.JakartaQueryUpdateTests;
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

/**
 * The subset of the Jakarta Data TCK that applies to a document search store: the tests this filter
 * disables need features Elasticsearch and OpenSearch repositories do not offer.
 */
public class FilterExtension implements ExecutionCondition {

    private static final String PROJECTION = "Selecting individual attributes is not supported by document search stores";

    private static final Map<Class<?>, String> DISABLED_CLASSES = Map.of(
        StatefulPersistenceEntityTests.class, "Micronaut Data does not implement Jakarta Data stateful repositories",
        // Both deploy the Catalog repository, which uses jakarta.persistence.query.NativeQuery from Jakarta Persistence 4.0
        NativeQueryTests.class, "jakarta.persistence.query.NativeQuery requires Jakarta Persistence 4.0",
        PersistenceEntityTests.class, "jakarta.persistence.query.NativeQuery requires Jakarta Persistence 4.0",
        // Micronaut Data delivers lifecycle events to ApplicationEventListener beans rather than CDI observers
        JakartaEventBuiltInRepositoryTest.class, "Jakarta Data lifecycle events are observed via CDI",
        JakartaEventCustomRepositoryTest.class, "Jakarta Data lifecycle events are observed via CDI",
        JakartaQueryUpdateTests.class, "Update queries are not supported by document search stores",
        ExpressionTests.class, "Functions such as LEFT, CONCAT, LENGTH and casts are not supported by document search stores",
        SortByExpressionTests.class, "Ordering by an expression is not supported by document search stores"
    );

    private static final Map<Class<?>, Map<String, String>> DISABLED_METHODS = Map.of(
        EntityTests.class, methods(PROJECTION, Set.of(
            "testCommonInterfaceQueries",
            "testFindEntitySelectAsRecordReturnList",
            "testLiteralInteger",
            "testPartialQuerySelectAndOrderBy",
            "testQueryWithNot",
            "testQueryWithNull",
            "testQueryWithSelectReturnsOptionalOfRecord",
            "testQueryWithSelectReturnsPageOfRecord",
            "testQueryWithoutSelectReturnsArrayOfRecord",
            "testQueryWithoutSelectReturnsListOfRecord",
            "testQueryWithoutSelectReturnsRecordWithSelect",
            "testQueryWithoutSelectReturnsStreamOfRecordWithSelect",
            "testRecordComponentsChooseAttributeReturnArray",
            "testRecordComponentsChooseAttributeReturnList",
            "testRecordComponentsChooseAttributeReturnOne",
            "testRecordComponentsChooseAttributeReturnOptional",
            "testRecordComponentsChooseAttributeReturnPage",
            "testRecordComponentsChooseAttributeReturnStream",
            "testReturnArrayOfRecordThatSelectsAttributes",
            "testReturnOptionalOfRecordThatSelectsAttributes",
            "testReturnPageOfRecordThatSelectsAttributes",
            "testReturnRecordThatSelectsAttributesFindEntity",
            "testReturnStreamOfRecordThatSelectsAttributesFindEntity",
            "testSelectEntityAttributeAsArrayOfLong",
            "testSelectEntityAttributeAsListOfLong",
            "testSelectEntityAttributeAsOne",
            "testSelectEntityAttributeAsOptional",
            "testSelectEntityAttributeAsPage",
            "testSelectEntityAttributeAsStreamOfLong",
            "testSelectEntityAttributesAsArrayOfRecord",
            "testSelectEntityAttributesAsListOfRecord",
            "testSelectEntityAttributesAsOptionalOfRecord",
            "testSelectEntityAttributesAsPageOfRecord"
        ), Map.of(
            "testUpdateQueryWithWhereClause", "Update queries are not supported by document search stores",
            "testUpdateQueryWithoutWhereClause", "Update queries are not supported by document search stores",
            // the cursor of these pages holds the sort values of a relational keyset, not the search_after values
            "testCursoredPageOf7FromCursor", "Cursors from other stores are not supported",
            "testFirstCursoredPageOf8AndNextPages", "Cursors from other stores are not supported"
        )),
        JakartaQueryTests.class, methods(PROJECTION, Set.of(
            "shouldReturnIdUsingIdFunctionOrderById",
            "shouldReturnName",
            "shouldReturnNameAndQuantity"
        ), Map.of()),
        ConstraintTests.class, methods(PROJECTION, Set.of("testAtMostConstraint"), Map.of()),
        RestrictionTests.class, Map.of("testUnmatchable", "A restriction between two parameters is not supported by document search stores")
    );

    private static Map<String, String> methods(String reason, Set<String> names, Map<String, String> others) {
        Map<String, String> methods = new java.util.HashMap<>(others);
        for (String name : names) {
            methods.put(name, reason);
        }
        return methods;
    }

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
        String reason = DISABLED_METHODS.getOrDefault(testClass, Map.of()).get(method);
        return reason == null ? ConditionEvaluationResult.enabled("") : ConditionEvaluationResult.disabled(reason);
    }
}
