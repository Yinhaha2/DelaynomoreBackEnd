from enum import StrEnum


class ErrorCode(StrEnum):
    INVALID_REQUEST = "INVALID_REQUEST"
    CONVERSATION_NOT_FOUND = "CONVERSATION_NOT_FOUND"
    AGENT_TIMEOUT = "AGENT_TIMEOUT"
    AGENT_ERROR = "AGENT_ERROR"
    CRAWL_FAILED = "CRAWL_FAILED"
    RATE_LIMITED = "RATE_LIMITED"


class AppError(Exception):
    def __init__(self, code: ErrorCode, message: str) -> None:
        self.code = code
        self.message = message
        super().__init__(message)


class ConversationNotFoundError(AppError):
    def __init__(self, conversation_id: str) -> None:
        super().__init__(
            ErrorCode.CONVERSATION_NOT_FOUND,
            f"会话不存在: {conversation_id}",
        )


class InvalidRequestError(AppError):
    def __init__(self, message: str) -> None:
        super().__init__(ErrorCode.INVALID_REQUEST, message)
