"""ChromaDB 저장소를 다루는 파일입니다.

이 파일은 ChromaDB 컬렉션 생성, 초기화, 문서 저장, 전체 문서 조회를 담당합니다.
검색 전략 자체는 app/retriever.py에서 처리합니다.
"""

from typing import Any

import chromadb
from chromadb.config import Settings

from app.config import CHROMA_DB_DIR, COLLECTION_NAME
from app.ollama_service import embed_text


def get_chroma_client() -> chromadb.PersistentClient:
    """ChromaDB 클라이언트를 만듭니다."""

    return chromadb.PersistentClient(
        path=str(CHROMA_DB_DIR),
        settings=Settings(anonymized_telemetry=False),
    )


def get_chroma_collection() -> Any:
    """문서 청크를 저장할 ChromaDB 컬렉션을 가져옵니다."""

    client = get_chroma_client()
    return client.get_or_create_collection(name=COLLECTION_NAME)


def reset_collection() -> None:
    """기존 컬렉션을 삭제하고 빈 컬렉션을 다시 만듭니다."""

    client = get_chroma_client()

    try:
        client.delete_collection(name=COLLECTION_NAME)
    except ValueError:
        # 컬렉션이 아직 없으면 삭제할 것도 없으므로 그냥 넘어갑니다.
        pass

    client.get_or_create_collection(name=COLLECTION_NAME)


def add_documents_with_embeddings(
    ids: list[str],
    texts: list[str],
    metadatas: list[dict[str, str | int]],
    embeddings: list[list[float]],
) -> None:
    """이미 계산된 임베딩과 문서 청크를 ChromaDB에 저장합니다."""

    collection = get_chroma_collection()
    collection.add(
        ids=ids,
        documents=texts,
        embeddings=embeddings,
        metadatas=metadatas,
    )


def rebuild_collection(
    ids: list[str],
    texts: list[str],
    metadatas: list[dict[str, str | int]],
) -> None:
    """전체 컬렉션을 새 문서 목록으로 교체합니다.

    임베딩을 먼저 모두 계산한 뒤 컬렉션을 초기화합니다.
    이렇게 하면 Ollama 연결 실패 시 기존 ChromaDB 데이터가 지워지는 문제를 줄일 수 있습니다.
    """

    embeddings = [embed_text(text) for text in texts]
    reset_collection()
    add_documents_with_embeddings(
        ids=ids,
        texts=texts,
        metadatas=metadatas,
        embeddings=embeddings,
    )


def get_all_documents() -> dict[str, list[Any]]:
    """키워드 검색에 사용할 전체 문서 청크를 가져옵니다."""

    collection = get_chroma_collection()
    return collection.get(include=["documents", "metadatas"])


def query_by_embedding(question_embedding: list[float], top_k: int) -> dict[str, list[Any]]:
    """질문 임베딩과 가까운 문서 청크를 ChromaDB에서 검색합니다."""

    collection = get_chroma_collection()
    return collection.query(
        query_embeddings=[question_embedding],
        n_results=top_k,
        include=["documents", "metadatas", "distances"],
    )
