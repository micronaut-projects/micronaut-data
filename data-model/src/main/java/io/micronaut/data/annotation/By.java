/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * <p>Annotates a parameter of a repository method, specifying a mapping to
 * a persistent field:</p>
 * <ul>
 * <li>if a {@linkplain #value field name} is specified, the parameter maps
 *     to the persistent field with the specified name, or
 * <li>if the special value {@value #ID} is specified, the parameter maps
 *     to the unique identifier field or property.
 * </ul>
 * <p>Arguments to the annotated parameter are compared to values of the
 * mapped persistent field.</p>
 * <p>The field name may be a compound name like {@code address.city}.</p>
 *
 * <p>For example, for a {@code Person} entity with attributes {@code ssn},
 * {@code firstName}, {@code lastName}, and {@code address} we might have:</p>
 *
 * <pre>
 * &#64;Repository
 * public interface People {
 *
 *     &#64;Find
 *     Person findById(&#64;By(ID) String id); // maps to Person.ssn
 *
 *     &#64;Find
 *     List&lt;Person&gt; findNamed(&#64;By("firstName") String first,
 *                            &#64;By("lastName") String last);
 *
 *     &#64;Find
 *     Person findByCity(&#64;By("address.city") String city);
 * }
 * </pre>
 *
 * <p>The {@code By} annotation is unnecessary when the method parameter name
 * matches the entity attribute name and the application is compiled with the
 * {@code -parameters} compiler option that makes parameter names available
 * at runtime.</p>
 *
 * <p>Thus, when this compiler option is enabled, the previous example may be
 * written without the use of {@code By}:</p>
 *
 * <pre>
 * &#64;Repository
 * public interface People {
 *
 *     &#64;Find
 *     Person findById(String ssn);
 *
 *     &#64;Find
 *     List&lt;Person&gt; findNamed(String firstName,
 *                            String lastname);
 *
 *     &#64;Find
 *     Person findByCity(String address_city);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface By {

    /**
     * The name of the persistent field mapped by the annotated parameter,
     * or {@value #ID} to indicate the unique identifier field or property
     * of the entity.
     *
     * @return the persistent field name, or {@value #ID} to indicate the
     *         unique identifier field.
     */
    String value();

    /**
     * The special value which indicates the unique identifier field or
     * property. The annotation {@code By(ID)} maps a parameter to the
     * identifier.
     * <p>
     * Note that {@code id(this)} is the expression in JPQL for the
     * unique identifier of an entity with an implicit identification
     * variable.
     */
    String ID = "id(this)";
}
