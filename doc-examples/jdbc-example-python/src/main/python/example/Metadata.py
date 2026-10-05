from dataclasses import dataclass

from micronaut.core.annotation import Introspected


# Backs the inline Python sample in src/main/docs/guide/dbc/sqlMapping/sqlJsonView.adoc
# tag::metadata[]
@Introspected
@dataclass
class Metadata:
    """The Json Duality View metadata.

    etag: A unique identifier for a specific version of the document, as a string of hexadecimal characters.
    asof: The latest system change number (SCN) for the JSON document, as a JSON number.
          This records the last logical point in time at which the document was generated.
    """
    etag: str
    asof: str
# end::metadata[]
