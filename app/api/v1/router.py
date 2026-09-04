from fastapi import APIRouter

from app.api.v1 import chat, conversations

router = APIRouter(prefix="/api/v1")
router.include_router(conversations.router)
router.include_router(chat.router)
