from fastapi import APIRouter

from app.deps import get_container
from app.schemas.chat import CreateConversationResponse

router = APIRouter(prefix="/conversations", tags=["conversations"])


@router.post("", response_model=CreateConversationResponse)
def create_conversation() -> CreateConversationResponse:
    return get_container().conversation_service.create()
