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

import io.micronaut.entities.Train;
import io.micronaut.entities.Train_;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import org.junit.jupiter.api.Test;

import static io.micronaut.MetamodelAssertionsUtils.assertMetaModelClassIsAnnotatedCorrectly;
import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class TrainMetamodelTest {

    @Test
    void generatedMetamodelHasExpectedFields_andDoesNotContainTransient() throws Exception {
        assertMetaModelClassIsAnnotatedCorrectly(Train_.class, Train.class);

        assertNotNull(Train_.class.getDeclaredField("id"));
        assertNotNull(Train_.class.getDeclaredField("name"));
        assertNotNull(Train_.class.getDeclaredField("model"));
        assertNotNull(Train_.class.getDeclaredField("capacity"));
        assertNotNull(Train_.class.getDeclaredField("speed"));
        assertNotNull(Train_.class.getDeclaredField("electric"));
        assertNotNull(Train_.class.getDeclaredField("departureTime"));
        assertNotNull(Train_.class.getDeclaredField("createdAt"));
        assertNotNull(Train_.class.getDeclaredField("departureDate"));
        assertNotNull(Train_.class.getDeclaredField("departureTimeOnly"));

        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("id").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("name").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("model").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("capacity").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("speed").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("electric").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("departureTime").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("createdAt").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("departureDate").getType().getName());
        assertEquals(SingularAttribute.class.getName(), Train_.class.getDeclaredField("departureTimeOnly").getType().getName());

        assertThrows(NoSuchFieldException.class, () -> Train_.class.getDeclaredField("transientField"));
        assertThrows(NoSuchFieldException.class, () -> Train_.class.getDeclaredField("FINAL_STATIC_FIELD"));

        MetamodelAssertionsUtils.assertClassFieldIsEntityType(Train_.class, EntityType.class, Train.class);
    }
}
