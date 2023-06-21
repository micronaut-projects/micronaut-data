package example

import jakarta.persistence.*

@Entity
data class Parent(

        val name: String,

        @OneToMany(mappedBy = "parent", cascade = [CascadeType.ALL])
        val children: List<Child>,

        @field:Id @GeneratedValue
        val id: Int? = null

)
