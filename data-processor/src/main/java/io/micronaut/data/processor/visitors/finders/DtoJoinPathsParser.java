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
package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Sergio del Amo
 */
public class DtoJoinPathsParser {
    public static final String CAPITALIZED_ID = "Id";

    @NonNull
    public static Set<String> joinPathsForDto(@NonNull ClassElement dto, @NonNull ClassElement query) {
        Set<String> paths = new HashSet<>();
        for (PropertyElement propertyElement : dto.getBeanProperties()) {
            final String name = propertyElement.getName();
            if (name.endsWith(CAPITALIZED_ID)) {
                String associationName = name.substring(0, name.indexOf(CAPITALIZED_ID));
                paths.add(associationName);
            }
        }
        return paths;
    }
}
