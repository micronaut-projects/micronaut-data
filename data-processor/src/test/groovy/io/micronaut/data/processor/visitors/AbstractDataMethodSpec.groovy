/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors

import io.micronaut.inject.ExecutableMethod

abstract class AbstractDataMethodSpec extends AbstractDataSpec {

    ExecutableMethod<?, ?> buildMethod(String entity ,String returnType, String method, String arguments, String...imports) {
        def repository = buildRepository('test.MyInterface', """

import io.micronaut.data.model.entities.*;
import io.micronaut.data.annotation.*;
import io.micronaut.data.model.*;
import java.util.*;
${imports ? imports.collect({ 'import ' + it + '.*;' }).join('\n') : ''}

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<$entity, Long> {

    $returnType $method($arguments);
}
""")
        repository.findPossibleMethods(method)
                .findFirst()
                .get()
    }
}
