from collections.abc import AsyncIterator
from datetime import datetime, timezone

from app.agents.base import AgentContext, AgentHandler
from app.core.exceptions import ConversationNotFoundError, InvalidRequestError
from app.core.ids import generate_conversation_id, generate_message_id, utc_now_iso
from app.schemas.chat import ChatStreamRequest, CreateConversationResponse
from app.store.base import ConversationStore
from app.store.models import Message
from app.streaming.chunks import TextDeltaChunk
from app.streaming.pipeline import StreamPipeline


class ConversationService:
    def __init__(self, store: ConversationStore) -> None:
        self._store = store

    def create(self) -> CreateConversationResponse:
        conversation_id = generate_conversation_id()
        created_at = datetime.now(timezone.utc)
        self._store.create_conversation(conversation_id, created_at)
        return CreateConversationResponse(
            conversation_id=conversation_id,
            created_at=utc_now_iso(),
        )

    def ensure_exists(self, conversation_id: str) -> None:
        if self._store.get_conversation(conversation_id) is None:
            raise ConversationNotFoundError(conversation_id)


class ChatService:
    def __init__(
        self,
        store: ConversationStore,
        agent: AgentHandler,
        pipeline: StreamPipeline | None = None,
    ) -> None:
        self._store = store
        self._agent = agent
        self._pipeline = pipeline or StreamPipeline()
        self._conversation_service = ConversationService(store)

    async def stream_chat(self, request: ChatStreamRequest) -> AsyncIterator[str]:
        message = request.message.strip()
        if not message:
            raise InvalidRequestError("message 不能为空")

        conversation_id = request.conversation_id
        if conversation_id is None:
            conversation_id = generate_conversation_id()
            self._store.create_conversation(conversation_id, datetime.now(timezone.utc))
        else:
            self._conversation_service.ensure_exists(conversation_id)

        user_message = Message(
            id=generate_message_id(),
            conversation_id=conversation_id,
            role="user",
            content=message,
        )
        self._store.save_message(user_message)

        assistant_message_id = generate_message_id()
        context = AgentContext(conversation_id=conversation_id, message=message)
        assistant_parts: list[str] = []

        async def tracked_agent_stream():
            async for chunk in self._agent.stream(context):
                if isinstance(chunk, TextDeltaChunk):
                    assistant_parts.append(chunk.content)
                yield chunk

        async for frame in self._pipeline.run(
            tracked_agent_stream(),
            conversation_id=conversation_id,
            message_id=assistant_message_id,
        ):
            yield frame

        assistant_message = Message(
            id=assistant_message_id,
            conversation_id=conversation_id,
            role="assistant",
            content="".join(assistant_parts),
        )
        self._store.save_message(assistant_message)
