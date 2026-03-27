/*
 * Copyright 2017-2026 original authors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.example;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public final class MetamodelAssertions {

    /**
     * Assert the generated metamodel {@code class_} field exists and is of the form
     * {@code managedJakartaType<entityClass>} (e.g. {@code EntityType<MyEntity>}).
     *
     * @param metamodelClass     generated metamodel class (e.g. {@code MyEntity_.class})
     * @param managedJakartaType expected raw type of {@code class_} (e.g. {@code EntityType.class})
     * @param entityClass        expected generic argument (e.g. {@code MyEntity.class})
     */
    public static void assertClassFieldIsEntityType(Class<?> metamodelClass, Class<?> managedJakartaType, Class<?> entityClass) throws Exception {
        Field f = metamodelClass.getDeclaredField("class_");

        assertEquals(managedJakartaType, f.getType(), "class_ raw type mismatch");

        Type gt = f.getGenericType();
        assertInstanceOf(ParameterizedType.class, gt, "class_ must be parameterized (EntityType<T>)");

        ParameterizedType pt = (ParameterizedType) gt;
        assertEquals(managedJakartaType, pt.getRawType(), "class_ raw generic type mismatch");
        assertEquals(entityClass, pt.getActualTypeArguments()[0], "class_ generic argument mismatch");
    }
}
