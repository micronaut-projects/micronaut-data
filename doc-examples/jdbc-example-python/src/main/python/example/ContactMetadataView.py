from dataclasses import dataclass
from typing import Annotated

from com.fasterxml.jackson.annotation import JsonProperty
from micronaut.data.annotation import GeneratedValue, Id, JsonView

from example.Contact import Contact
from example.Metadata import Metadata


# Backs the inline Python sample in src/main/docs/guide/dbc/sqlMapping/sqlJsonView.adoc
@JsonView(value="CONTACT_VIEW", alias="cv", entity=Contact)
@dataclass
class ContactMetadataView:
    name: str
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
    # tag::metadata[]
    metadata: Annotated[Metadata | None, JsonProperty("_metadata")] = None
    # end::metadata[]
