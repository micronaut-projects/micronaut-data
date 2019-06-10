package io.micronaut.data.processor.visitors

import io.micronaut.inject.ExecutableMethod

abstract class AbstractPredatorMethodSpec extends AbstractPredatorSpec {

    ExecutableMethod<?, ?> buildMethod(String returnType, String method, String arguments, String...imports) {
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.*;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;
${imports ? imports.collect({ 'import ' + it + '.*;' }).join('\n') : ''}

@Repository
interface MyInterface extends GenericRepository<Person, Long> {

    $returnType $method($arguments);
}
""")
        repository.findPossibleMethods(method)
                .findFirst()
                .get()
    }
}
