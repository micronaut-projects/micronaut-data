package io.micronaut.data.graph.runtime.operations

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.graph.annotation.Direction
import io.micronaut.data.graph.model.query.PathDirectionParameter
import io.micronaut.data.graph.model.query.PathQueryDefinition
import io.micronaut.data.model.runtime.PreparedQuery
import spock.lang.Specification

class ApplyDirectionSpec extends Specification {

    void "render the @PathDirection argument in place of the direction tokens"() {
        given:
        def query = new StringBuilder("MATCH (a)" + PathQueryDefinition.PATH_LEFT_TOKEN + "[r]" + PathQueryDefinition.PATH_RIGHT_TOKEN + "(b)")

        when:
        AbstractGraphRepositoryOperations.applyDirection(preparedQuery(1, ["x", Direction.INCOMING] as Object[]), query)

        then:
        query.toString() == "MATCH (a)<-[r]-(b)"
    }

    void "reject a @PathDirection index outside the arguments"() {
        given:
        def query = new StringBuilder("MATCH (a)" + PathQueryDefinition.PATH_LEFT_TOKEN + "[r]" + PathQueryDefinition.PATH_RIGHT_TOKEN + "(b)")

        when:
        AbstractGraphRepositoryOperations.applyDirection(preparedQuery(index, ["x"] as Object[]), query)

        then:
        def e = thrown(DataAccessException)
        e.message.startsWith("Path query has no @PathDirection argument at index " + index + ": MATCH (a)")

        where:
        index << [1, -1]
    }

    private PreparedQuery<?, ?> preparedQuery(int index, Object[] arguments) {
        def metadata = Stub(AnnotationMetadata) {
            getAnnotation(PathDirectionParameter) >> AnnotationValue.builder(PathDirectionParameter).value(index).build()
        }
        return Stub(PreparedQuery) {
            getAnnotationMetadata() >> metadata
            getParameterArray() >> arguments
        }
    }
}
