from app.agents.base import AgentHandler
from app.agents.stub_agent import StubAgentHandler
from app.config import settings


def create_agent_handler() -> AgentHandler:
    handlers: dict[str, type[AgentHandler]] = {
        "stub": StubAgentHandler,
    }
    handler_cls = handlers.get(settings.handler, StubAgentHandler)
    return handler_cls()
