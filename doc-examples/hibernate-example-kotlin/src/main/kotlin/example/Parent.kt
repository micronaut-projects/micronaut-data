package example

import jakarta.persistence.*

@Entity
data class Parent(

        val name: String,

        @OneToMany(mappedBy = "parent", cascade = [CascadeType.ALL])
        val children: MutableList<Child>,

        @field:Id @GeneratedValue
        val id: Int? = null

)
