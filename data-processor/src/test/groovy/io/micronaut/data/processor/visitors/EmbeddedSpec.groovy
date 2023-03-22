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

    void "test compile entity with inner class as embedded key"() {
        given:
        def repository = buildRepository('test.OwnerPetRepository', """
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

import io.micronaut.core.annotation.Creator;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "owners_pets",
    schema = "myschema",
    catalog = "mycatalog"
)
class OwnerPet {
    @EmbeddedId
    private InnerOwnerPetKey id;
    @Creator
    public OwnerPet(final InnerOwnerPetKey id) {
        this.id = id;
    }
    public InnerOwnerPetKey getId() {
        return this.id;
    }
    public void setId(final InnerOwnerPetKey id) {
        this.id = id;
    }
    @Embeddable
    public class InnerOwnerPetKey {
        @Column(name = "user_id")
        @ManyToOne(optional = false)
        private @NotNull Owner owner;
        @Column(name = "role_name")
        @ManyToOne(optional = false)
        private @NotNull Pet pet;

        @Creator
        public InnerOwnerPetKey(final Owner owner, final Pet pet) {
            this.owner = owner;
            this.pet = pet;
        }

        public Owner getOwner() {
            return this.owner;
        }

        public void setOwner(final Owner owner) {
            this.owner = owner;
        }

        public Pet getPet() {
            return this.pet;
        }

        public void setPet(final Pet pet) {
            this.pet = pet;
        }
    }

}

@Entity
@Table(
    name = "owners",
    schema = "myschema",
    catalog = "mycatalog"
)
class Owner {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private int age;

    @Creator
    public Owner(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

}

@Entity
@Table(
    name = "pets",
    schema = "myschema",
    catalog = "mycatalog"
)
class Pet {

    @Id
    @AutoPopulated
    private UUID id;
    private String name;
    @ManyToOne
    private Owner owner;
    private PetType type = PetType.DOG;

    @Creator
    public Pet(String name, @io.micronaut.core.annotation.Nullable Owner owner) {
        this.name = name;
        this.owner = owner;
    }

    public Owner getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public UUID getId() {
        return id;
    }

    public PetType getType() {
        return type;
    }

    public void setType(PetType type) {
        this.type = type;
    }

    public void setId(UUID id) {
        this.id = id;
    }


    public enum PetType {
        DOG,
        CAT
    }
}

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface OwnerPetRepository extends CrudRepository<OwnerPet, OwnerPet.InnerOwnerPetKey> {
}

"""
        )

        expect:
        repository != null
    }

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

    void "test jdbc compile embedded id count query"() {
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

@io.micronaut.data.jdbc.annotation.JdbcRepository
@io.micronaut.context.annotation.Executable
interface LikeRepository extends CrudRepository<Like, LikeId> {
    long countByLikeIdImageIdentifier(UUID likeIdImageIdentifier);
}

"""
        )

        expect:
        repository != null
        repository.getRequiredMethod("deleteAll", Iterable).stringValue(Query).get() ==
                    'DELETE  FROM "likes"  WHERE ("like_id_image_identifier" = ? AND "like_id_user_identifier" = ?)'
    }

}
