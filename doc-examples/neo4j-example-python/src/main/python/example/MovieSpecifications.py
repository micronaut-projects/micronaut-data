from micronaut.data.graph.model.criteria import GraphCriteria


# tag::specifications[]
def released_after(year: int):  # <1>
    return lambda root, criteria_builder: criteria_builder.greaterThan(root.get("released"), year)


def directed_by(name: str):  # <2>
    return lambda root, criteria_builder: criteria_builder.equal(root.join("directors").get("name"), name)
# end::specifications[]


# tag::graphCriteria[]
def with_roles():  # <1>
    return lambda root, criteria_builder: GraphCriteria.related(root, "roles")


def connected_to(title: str, hops: int):  # <2>
    return lambda root, criteria_builder: GraphCriteria.connected(root, None, criteria_builder.literal(title), hops)
# end::graphCriteria[]
