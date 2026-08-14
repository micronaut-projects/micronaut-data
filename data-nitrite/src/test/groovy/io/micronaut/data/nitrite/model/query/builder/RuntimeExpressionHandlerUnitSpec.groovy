package io.micronaut.data.nitrite.model.query.builder

import io.micronaut.data.model.jpa.criteria.impl.expression.LiteralExpression
import spock.lang.Specification

class RuntimeExpressionHandlerUnitSpec extends Specification {

    void "handleRegex unwraps literal expressions for runtime LIKE"() {
        given:
        def handler = new RuntimeExpressionHandler()
        def queryState = new NitriteQueryState()

        expect:
        handler.handleRegex("hexadecimal", false, false, false, false, new LiteralExpression("4_"), true, null, queryState, null) == '(?s)^4.$'
        handler.handleRegex("hexadecimal", false, false, false, false, new LiteralExpression(new LiteralExpression("4_")), true, null, queryState, null) == '(?s)^4.$'
    }

    void "handleRegex quotes regex metacharacters in literal values"() {
        given:
        def handler = new RuntimeExpressionHandler()
        def queryState = new NitriteQueryState()

        expect:
        handler.handleRegex("field", false, false, true, true, new LiteralExpression("a.b[0]"), false, null, queryState, null) == '^\\Qa.b[0]\\E$'
    }

    void "resolveValue unwraps literal expressions"() {
        given:
        def handler = new RuntimeExpressionHandler()
        def queryState = new NitriteQueryState()

        expect:
        handler.resolveValue(queryState, null, new LiteralExpression(72L)) == 72L
        handler.resolveValue(queryState, null, new LiteralExpression(new LiteralExpression(72L))) == 72L
    }

    void "resolveCollectionValue unwraps literal iterable expressions"() {
        given:
        def handler = new RuntimeExpressionHandler()
        def queryState = new NitriteQueryState()

        expect:
        handler.resolveCollectionValue(queryState, null, new LiteralExpression(["E", "G"])) == ["E", "G"]
        handler.resolveCollectionValue(queryState, null, new LiteralExpression(new LiteralExpression(["E", "G"]))) == ["E", "G"]
    }
}
