from abc import ABC, abstractmethod
from collections.abc import AsyncIterator
from dataclasses import dataclass

from app.streaming.chunks import ContentChunk


@dataclass
class AgentContext:
    conversation_id: str
    message: str


class AgentHandler(ABC):
    """Agent 处理层抽象，与 HTTP / SSE 解耦。"""

    @abstractmethod
    async def stream(self, context: AgentContext) -> AsyncIterator[ContentChunk]:
        raise NotImplementedError
