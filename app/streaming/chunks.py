from enum import StrEnum
from typing import Annotated, Literal, Union

from pydantic import BaseModel, Field


class SseEventName(StrEnum):
    CHUNK = "chunk"
    DONE = "done"
    ERROR = "error"


class TextDeltaChunk(BaseModel):
    type: Literal["text_delta"] = "text_delta"
    content: str


class ImageChunk(BaseModel):
    type: Literal["image"] = "image"
    url: str
    alt: str | None = None


class LinkChunk(BaseModel):
    type: Literal["link"] = "link"
    url: str
    title: str
    description: str | None = None


class DoneChunk(BaseModel):
    type: Literal["done"] = "done"
    message_id: str
    conversation_id: str


class ErrorChunk(BaseModel):
    type: Literal["error"] = "error"
    code: str
    message: str


StreamChunk = Annotated[
    Union[TextDeltaChunk, ImageChunk, LinkChunk, DoneChunk, ErrorChunk],
    Field(discriminator="type"),
]

ContentChunk = Union[TextDeltaChunk, ImageChunk, LinkChunk]
