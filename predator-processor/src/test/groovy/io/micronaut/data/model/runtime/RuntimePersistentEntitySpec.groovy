package io.micronaut.data.model.runtime

import io.micronaut.data.annotation.Relation
import io.micronaut.data.model.Association
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.tck.entities.Face
import io.micronaut.data.tck.entities.Nose
import spock.lang.Specification

class RuntimePersistentEntitySpec extends Specification {
    void 'test one-to-one'() {
        given:
        PersistentEntity face = PersistentEntity.of(Face)
        PersistentEntity nose = PersistentEntity.of(Nose)

        def noseAss = face.getPropertyByName("nose")
        expect:
        noseAss instanceof Association
        noseAss.kind == Relation.Kind.ONE_TO_ONE
        noseAss.isForeignKey()
    }

}
