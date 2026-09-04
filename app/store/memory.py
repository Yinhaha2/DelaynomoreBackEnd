from datetime import datetime

from app.store.base import ConversationStore
from app.store.models import Conversation, Message


class InMemoryConversationStore(ConversationStore):
    def __init__(self) -> None:
        self._conversations: dict[str, Conversation] = {}
        self._messages: list[Message] = []

    def create_conversation(self, conversation_id: str, created_at: datetime) -> Conversation:
        conversation = Conversation(id=conversation_id, created_at=created_at)
        self._conversations[conversation_id] = conversation
        return conversation

    def get_conversation(self, conversation_id: str) -> Conversation | None:
        return self._conversations.get(conversation_id)

    def save_message(self, message: Message) -> None:
        self._messages.append(message)
