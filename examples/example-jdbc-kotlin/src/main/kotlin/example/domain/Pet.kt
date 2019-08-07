package example.domain

import io.micronaut.data.annotation.AutoPopulated
import javax.persistence.*
import java.util.UUID

@Entity
data class Pet (@Id
                @AutoPopulated
                var id: UUID?,
                val name: String,
                @ManyToOne
                val owner: Owner,
                val type : PetType = PetType.DOG) {

    enum class PetType {
        DOG,
        CAT
    }
}