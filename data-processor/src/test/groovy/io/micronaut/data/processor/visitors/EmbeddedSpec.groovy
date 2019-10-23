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
