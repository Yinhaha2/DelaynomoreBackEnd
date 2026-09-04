from abc import ABC, abstractmethod
from datetime import datetime

from app.store.models import Conversation, Message


class ConversationStore(ABC):
    @abstractmethod
    def create_conversation(self, conversation_id: str, created_at: datetime) -> Conversation:
        raise NotImplementedError

    @abstractmethod
    def get_conversation(self, conversation_id: str) -> Conversation | None:
        raise NotImplementedError

    @abstractmethod
    def save_message(self, message: Message) -> None:
        raise NotImplementedError
