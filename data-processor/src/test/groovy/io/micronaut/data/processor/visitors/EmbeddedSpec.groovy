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

import io.micronaut.data.annotation.Query

class EmbeddedSpec extends AbstractDataSpec {

    void "test compile embedded id count query"() {
        given:
        def repository = buildRepository('test.LikeRepository', """
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

@javax.persistence.Entity
@javax.persistence.Table(name = "likes")
class Like{
    @javax.persistence.EmbeddedId private LikeId likeId;
    
    public void setLikeId(LikeId likeId) {
        this.likeId = likeId;
    }        
    
    public LikeId getLikeId() {
        return likeId;
    }
}

@javax.persistence.Embeddable
class LikeId {
    private UUID imageIdentifier;
    private UUID userIdentifier;
        
    public UUID getImageIdentifier() {
        return imageIdentifier;
    }
    
    public void setImageIdentifier(UUID uuid) {
        imageIdentifier = uuid;
    }
    
    public UUID getUserIdentifier() {
        return userIdentifier;
    }
    
    public void setUserIdentifier(UUID uuid) {
        userIdentifier = uuid;
    }
}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface LikeRepository extends CrudRepository<Like, LikeId> {
    long countByLikeIdImageIdentifier(UUID likeIdImageIdentifier);
}
"""
        )

        expect:
        repository != null
        repository.getRequiredMethod("countByLikeIdImageIdentifier", UUID).stringValue(Query).get() ==
            'SELECT COUNT(*) FROM "likes" like_ WHERE (like_."like_id_image_identifier" = ?)'
    }

}
