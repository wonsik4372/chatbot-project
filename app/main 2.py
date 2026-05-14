"""FastAPI 서버 시작 파일입니다.

사용자는 이 서버에 질문을 보내고, 서버는 RAG 로직을 사용해서 답변을 반환합니다.
"""

from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.rag import answer_question


app = FastAPI(
    title="동의대학교 컴퓨터소프트웨어공학전공 안내 RAG 챗봇",
    description="1단계: TXT 파일만 사용하는 최소 RAG 챗봇",
    version="0.1.0",
)


class ChatRequest(BaseModel):
    """사용자가 /chat API로 보낼 요청 형식입니다."""

    question: str = Field(..., description="챗봇에게 물어볼 질문")


class Source(BaseModel):
    """답변 생성에 참고한 문서 출처 형식입니다."""

    source: str
    chunk_index: int


class ChatResponse(BaseModel):
    """챗봇이 사용자에게 돌려줄 응답 형식입니다."""

    answer: str
    sources: list[Source]


@app.get("/")
def health_check() -> dict[str, str]:
    """서버가 정상 실행 중인지 확인하는 기본 API입니다."""

    return {"message": "RAG 챗봇 서버가 실행 중입니다."}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> dict:
    """사용자 질문을 받아 RAG 답변을 반환합니다."""

    return answer_question(request.question)
