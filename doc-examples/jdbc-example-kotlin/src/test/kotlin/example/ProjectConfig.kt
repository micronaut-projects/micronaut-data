package example

import io.kotest.core.config.AbstractProjectConfig
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension

object ProjectConfig : AbstractProjectConfig() {
    override fun listeners() = listOf(MicronautKotest5Extension)
    override fun extensions() = listOf(MicronautKotest5Extension)
}
