package io.micronaut.data.runtime.operations.internal.query

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.QueryResultInfo
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.model.vector.search.Similarity
import io.micronaut.data.model.runtime.QueryParameterBinding
import io.micronaut.data.model.runtime.StoredQuery
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery
import spock.lang.Specification

class DefaultBindableParametersStoredQuerySpec extends Specification {

    def "expandable iterable binds many"() {
        given:
        def binding = new SimpleBinding(
                name: "lst",
                value: [1, 2, 3],
                expandable: true
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder()

        and:
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        when:
        q.bindParameters(binder, null, null, null)

        then:
        binder.oneCalls.size() == 0
        binder.manyCalls.size() == 1
        binder.manyCalls[0].values == [1, 2, 3]
        binder.manyCalls[0].binding.is(binding)
    }

    def "expandable empty primitive array binds one null"() {
        given:
        def binding = new SimpleBinding(
                name: "arr",
                value: new int[0],
                expandable: true
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder()
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        when:
        q.bindParameters(binder, null, null, null)

        then: "empty expansion results in bindOne(null)"
        binder.manyCalls.isEmpty()
        binder.oneCalls.size() == 1
        binder.oneCalls[0].value == null
        binder.oneCalls[0].binding.is(binding)
    }

    def "byte[] is not expanded and binds one"() {
        given:
        def bytes = [1 as byte, 2 as byte] as byte[]
        def binding = new SimpleBinding(
                name: "bytes",
                value: bytes,
                expandable: true // even if expandable, byte[] must not expand
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder()
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        when:
        q.bindParameters(binder, null, null, null)

        then:
        binder.manyCalls.isEmpty()
        binder.oneCalls.size() == 1
        binder.oneCalls[0].value instanceof byte[]
        (binder.oneCalls[0].value as byte[]).toList() == bytes.toList()
    }

    def "parameter converter is used when provided"() {
        given:
        def binding = new SimpleBinding(
                name: "x",
                value: 7,
                parameterConverterClass: DummyConverter
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder() {
            @Override
            Object convert(Class<?> converterClass, Object value, Argument<?> argument) {
                assert converterClass == DummyConverter
                return "converted-$value"
            }
        }
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        when:
        q.bindParameters(binder, null, null, null)

        then:
        binder.manyCalls.isEmpty()
        binder.oneCalls.size() == 1
        binder.oneCalls[0].value == "converted-7"
    }

    def "requires invocation context when parameter index is used"() {
        given:
        def binding = new SimpleBinding(
                name: "p0",
                parameterIndex: 0
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder()
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        when:
        q.bindParameters(binder, null, null, null)

        then:
        def e = thrown(NullPointerException)
        e.message.contains("Invocation context is required")
    }

    def "similarity parameter is normalized to score when scoring function is present"() {
        given:
        def binding = new SimpleBinding(
                name: "similarity",
                parameterIndex: 0
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder()
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        def invocationContext = Mock(MethodInvocationContext) {
            getParameterValues() >> ([new Similarity(0.5d), ScoringFunction.COSINE] as Object[])
            getArguments() >> ([Argument.of(Similarity), Argument.of(ScoringFunction)] as Argument[])
        }

        when:
        q.bindParameters(binder, invocationContext, null, null)

        then:
        binder.oneCalls.size() == 1
        binder.oneCalls[0].value == 1d
    }

    def "similarity parameter remains raw when no scoring function is present"() {
        given:
        def binding = new SimpleBinding(
                name: "similarity",
                parameterIndex: 0
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder()
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        and:
        def invocationContext = Mock(MethodInvocationContext) {
            getParameterValues() >> ([new Similarity(0.5d)] as Object[])
            getArguments() >> ([Argument.of(Similarity)] as Argument[])
        }

        when:
        q.bindParameters(binder, invocationContext, null, null)

        then:
        binder.oneCalls.size() == 1
        binder.oneCalls[0].value == 0.5d
    }

    def "similarity parameter uses dialect default scoring function for sql stored query"() {
        given:
        def binding = new SimpleBinding(
                name: "similarity",
                parameterIndex: 0
        )
        def sq = new SimpleSqlStoredQuery([binding], Dialect.POSTGRES)
        def binder = new CapturingBinder()
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        and:
        def invocationContext = Mock(MethodInvocationContext) {
            getParameterValues() >> ([new Similarity(0.5d)] as Object[])
            getArguments() >> ([Argument.of(Similarity)] as Argument[])
        }

        when:
        q.bindParameters(binder, invocationContext, null, null)

        then:
        binder.oneCalls.size() == 1
        binder.oneCalls[0].value == 1d
    }

    def "unsupported normalizer function falls back to identity conversion"() {
        given:
        def binding = new SimpleBinding(
                name: "similarity",
                parameterIndex: 0
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder()
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        and:
        def invocationContext = Mock(MethodInvocationContext) {
            getParameterValues() >> ([new Similarity(0.5d), ScoringFunction.L1_MANHATTAN] as Object[])
            getArguments() >> ([Argument.of(Similarity), Argument.of(ScoringFunction)] as Argument[])
        }

        when:
        q.bindParameters(binder, invocationContext, null, null)

        then:
        binder.oneCalls.size() == 1
        binder.oneCalls[0].value == 0.5d
    }

    def "multiple scoring function parameters are rejected"() {
        given:
        def binding = new SimpleBinding(
                name: "similarity",
                parameterIndex: 0
        )
        def sq = new SimpleStoredQuery([binding])
        def binder = new CapturingBinder()
        def q = new DefaultBindableParametersStoredQuery<Object, Object>(sq, null, ConversionService.SHARED)

        and:
        def invocationContext = Mock(MethodInvocationContext) {
            getParameterValues() >> ([new Similarity(0.5d), ScoringFunction.COSINE, ScoringFunction.L2_EUCLIDEAN] as Object[])
            getArguments() >> ([Argument.of(Similarity), Argument.of(ScoringFunction), Argument.of(ScoringFunction)] as Argument[])
        }

        when:
        q.bindParameters(binder, invocationContext, null, null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("Only one ScoringFunction parameter")
    }



    // --- helpers ---

    static class DummyConverter {}

    static class SimpleBinding implements QueryParameterBinding {
        String name
        Object value
        boolean expandable
        int parameterIndex = -1
        Class<?> parameterConverterClass
        boolean expression = false

        @Override
        String getName() { return name }

        @Override
        Object getValue() { return value }

        @Override
        boolean isExpandable() { return expandable }

        @Override
        int getParameterIndex() { return parameterIndex }

        @Override
        Class<?> getParameterConverterClass() { return parameterConverterClass }

        @Override
        boolean isExpression() { return expression }
    }

    static class SimpleStoredQuery implements StoredQuery<Object, Object> {
        final List<QueryParameterBinding> bindings

        SimpleStoredQuery(List<QueryParameterBinding> bindings) {
            this.bindings = bindings
        }

        @Override
        String getName() { "Q" }

        @Override
        Class<Object> getRootEntity() { Object.class }

        @Override
        boolean hasPageable() { false }

        @Override
        String getQuery() { "Q" }

        @Override
        String[] getExpandableQueryParts() { new String[0] }

        @Override
        List<QueryParameterBinding> getQueryBindings() { bindings }

        @Override
        Class<Object> getResultType() { Object.class }

        @Override
        Argument<Object> getResultArgument() { Argument.OBJECT_ARGUMENT }

        @Override
        DataType getResultDataType() { DataType.OBJECT }

        @Override
        OperationType getOperationType() { OperationType.QUERY }

        @Override
        boolean isCount() { false }

        @Override
        boolean hasResultConsumer() { false }

        @Override
        boolean isRawQuery() { false }
    }

    static final class SimpleSqlStoredQuery extends SimpleStoredQuery implements SqlStoredQuery<Object, Object> {
        private final Dialect dialect

        SimpleSqlStoredQuery(List<QueryParameterBinding> bindings, Dialect dialect) {
            super(bindings)
            this.dialect = dialect
        }

        @Override
        boolean isExpandableQuery() { false }

        @Override
        Dialect getDialect() { dialect }

        @Override
        SqlQueryBuilder getQueryBuilder() { null }

        @Override
        Map<QueryParameterBinding, Object> collectAutoPopulatedPreviousValues(Object entity) { null }

        @Override
        QueryResultInfo getQueryResultInfo() { null }

        @Override
        io.micronaut.data.model.runtime.RuntimePersistentEntity<Object> getPersistentEntity() { null }

        @Override
        void bindParameters(BindableParametersStoredQuery.Binder binder,
                            io.micronaut.aop.InvocationContext<?, ?> invocationContext,
                            Object entity,
                            Map<QueryParameterBinding, Object> previousValues) {
            throw new UnsupportedOperationException("Not used in this test")
        }
    }

    static class CapturingBinder implements BindableParametersStoredQuery.Binder {
        static class OneCall { QueryParameterBinding binding; Object value }
        static class ManyCall { QueryParameterBinding binding; List<Object> values }

        final List<OneCall> oneCalls = []
        final List<ManyCall> manyCalls = []

        @Override
        Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) { return null }

        @Override
        Object convert(Object value, RuntimePersistentProperty<?> property) { return value }

        @Override
        Object convert(Class<?> converterClass, Object value, Argument<?> argument) { return value }

        @Override
        void bindOne(QueryParameterBinding binding, Object value) {
            oneCalls << new OneCall(binding: binding, value: value)
        }

        @Override
        void bindMany(QueryParameterBinding binding, Collection<Object> values) {
            manyCalls << new ManyCall(binding: binding, values: new ArrayList<>(values))
        }
    }

}
