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

package io.micronaut;

import io.micronaut.entities.Client;
import io.micronaut.entities.Client_;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.*;
import org.junit.jupiter.api.Test;

import static io.micronaut.MetamodelAssertionsUtils.assertMetaModelClassIsAnnotatedCorrectly;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class ClientMetamodelTest {

    @Test
    void generatedMetamodelHasExpectedFields_andTypes() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(Client_.class, Client.class);

        assertNotNull(Client_.class.getDeclaredField("id"));
        assertNotNull(Client_.class.getDeclaredField("name"));
        assertNotNull(Client_.class.getDeclaredField("version"));
        assertNotNull(Client_.class.getDeclaredField("tier"));
        assertNotNull(Client_.class.getDeclaredField("createdAt"));
        assertNotNull(Client_.class.getDeclaredField("billingAddress"));

        assertNotNull(Client_.class.getDeclaredField("categoriesCollection"));
        assertNotNull(Client_.class.getDeclaredField("categoriesList"));
        assertNotNull(Client_.class.getDeclaredField("categoriesSet"));
        assertNotNull(Client_.class.getDeclaredField("mainCategory"));

        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("version").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("tier").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("createdAt").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("billingAddress").getType().getName());

        assertEquals(CollectionAttribute.class.getName(), Client_.class.getDeclaredField("categoriesCollection").getType().getName());
        assertEquals(ListAttribute.class.getName(), Client_.class.getDeclaredField("categoriesList").getType().getName());
        assertEquals(SetAttribute.class.getName(), Client_.class.getDeclaredField("categoriesSet").getType().getName());

        assertEquals(SingularAttribute.class.getName(), Client_.class.getDeclaredField("mainCategory").getType().getName());

        assertThrows(NoSuchFieldException.class, () -> Client_.class.getDeclaredField("nonPersistent"));
        MetamodelAssertionsUtils.assertClassFieldIsEntityType(Client_.class, EntityType.class, Client.class);
    }
}
