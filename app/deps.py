from dataclasses import dataclass

from app.agents.factory import create_agent_handler
from app.services.chat_service import ChatService, ConversationService
from app.store.memory import InMemoryConversationStore
from app.streaming.pipeline import StreamPipeline


@dataclass
class AppContainer:
    store: InMemoryConversationStore
    conversation_service: ConversationService
    chat_service: ChatService


_container: AppContainer | None = None


def get_container() -> AppContainer:
    global _container
    if _container is None:
        store = InMemoryConversationStore()
        agent = create_agent_handler()
        pipeline = StreamPipeline()
        _container = AppContainer(
            store=store,
            conversation_service=ConversationService(store),
            chat_service=ChatService(store, agent, pipeline),
        )
    return _container


def reset_container() -> None:
    global _container
    _container = None
