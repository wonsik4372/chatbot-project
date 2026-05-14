"""FastAPI 서버 시작 파일입니다.

사용자는 이 서버에 질문을 보내고, 서버는 RAG 로직을 사용해서 답변을 반환합니다.
"""

from fastapi import FastAPI
from pydantic import BaseModel, Field

from app.rag import answer_question
from app.retriever import search_documents


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


class SearchResult(BaseModel):
    """질문과 비슷해서 검색된 문서 청크 형식입니다."""

    source: str
    chunk_index: int
    distance: float
    text: str


@app.get("/")
def health_check() -> dict[str, str]:
    """서버가 정상 실행 중인지 확인하는 기본 API입니다."""

    return {"message": "RAG 챗봇 서버가 실행 중입니다."}


@app.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest) -> dict:
    """사용자 질문을 받아 RAG 답변을 반환합니다."""

    return answer_question(request.question)


@app.post("/search", response_model=list[SearchResult])
def search(request: ChatRequest) -> list[dict]:
    """질문했을 때 어떤 문서 청크가 검색되는지 확인하는 점검용 API입니다.

    답변이 이상할 때 이 API를 먼저 확인하면,
    검색 단계에서 관련 자료를 못 찾은 것인지 LLM 답변 생성 단계의 문제인지 구분할 수 있습니다.
    """

    documents = search_documents(question=request.question, top_k=5)

    results = []
    for doc in documents:
        metadata = doc["metadata"]
        results.append(
            {
                "source": str(metadata.get("source", "알 수 없는 출처")),
                "chunk_index": int(metadata.get("chunk_index", 0)),
                "distance": float(doc["distance"]),
                "text": doc["text"],
            }
        )

    return results
