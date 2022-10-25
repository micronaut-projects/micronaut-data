package io.micronaut.data.azure.reactive

import io.micronaut.data.azure.CosmosBasicSpec
import spock.lang.IgnoreIf


@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class ReactiveCosmosBasicSpec extends CosmosBasicSpec implements CosmosReactiveConfigured {
}
