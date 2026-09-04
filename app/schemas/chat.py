from pydantic import BaseModel, Field


class CreateConversationResponse(BaseModel):
    conversation_id: str
    created_at: str


class ChatStreamRequest(BaseModel):
    message: str = Field(min_length=1)
    conversation_id: str | None = None
