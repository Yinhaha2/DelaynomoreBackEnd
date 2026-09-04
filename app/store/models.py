from dataclasses import dataclass
from datetime import datetime


@dataclass
class Conversation:
    id: str
    created_at: datetime


@dataclass
class Message:
    id: str
    conversation_id: str
    role: str
    content: str
