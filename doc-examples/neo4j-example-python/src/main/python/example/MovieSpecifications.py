# tag::specifications[]
def released_after(year: int):  # <1>
    return lambda root, criteria_builder: criteria_builder.greaterThan(root.get("released"), year)


def directed_by(name: str):  # <2>
    return lambda root, criteria_builder: criteria_builder.equal(root.join("directors").get("name"), name)
# end::specifications[]
