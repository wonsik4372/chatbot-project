"""ChromaDB와 Ollama 임베딩을 다루는 파일입니다.

이 파일은 크게 두 가지 일을 합니다.
1. 문장이나 문서를 Ollama 임베딩 모델로 숫자 벡터로 바꿉니다.
2. ChromaDB에 문서 청크를 저장하거나 검색합니다.
"""

from typing import Any

import chromadb
import ollama

from app.config import CHROMA_DB_DIR, COLLECTION_NAME, EMBEDDING_MODEL, OLLAMA_HOST


def get_ollama_client() -> ollama.Client:
    """Ollama 서버와 통신하는 클라이언트를 만듭니다."""

    return ollama.Client(host=OLLAMA_HOST)


def get_chroma_collection() -> Any:
    """ChromaDB 컬렉션을 가져옵니다.

    컬렉션은 문서 청크와 임베딩을 저장하는 공간입니다.
    컬렉션이 아직 없으면 자동으로 새로 만듭니다.
    """

    client = chromadb.PersistentClient(path=str(CHROMA_DB_DIR))
    return client.get_or_create_collection(name=COLLECTION_NAME)


def embed_text(text: str) -> list[float]:
    """텍스트 하나를 Ollama 임베딩 모델로 벡터로 변환합니다."""

    client = get_ollama_client()
    response = client.embeddings(model=EMBEDDING_MODEL, prompt=text)
    return response["embedding"]


def add_documents(
    ids: list[str],
    texts: list[str],
    metadatas: list[dict[str, str | int]],
) -> None:
    """여러 문서 청크를 ChromaDB에 저장합니다.

    ChromaDB에 같은 id가 이미 있으면 중복 문제가 생길 수 있으므로,
    이 예제에서는 ingest 스크립트에서 컬렉션을 먼저 비우고 다시 저장합니다.
    """

    collection = get_chroma_collection()
    embeddings = [embed_text(text) for text in texts]

    collection.add(
        ids=ids,
        documents=texts,
        embeddings=embeddings,
        metadatas=metadatas,
    )


def search_similar_documents(question: str, top_k: int) -> list[dict[str, Any]]:
    """질문과 의미가 가까운 문서 청크를 ChromaDB에서 찾습니다."""

    collection = get_chroma_collection()
    question_embedding = embed_text(question)

    result = collection.query(
        query_embeddings=[question_embedding],
        n_results=top_k,
        include=["documents", "metadatas", "distances"],
    )

    documents = result.get("documents", [[]])[0]
    metadatas = result.get("metadatas", [[]])[0]
    distances = result.get("distances", [[]])[0]

    found_docs = []
    for document, metadata, distance in zip(documents, metadatas, distances):
        found_docs.append(
            {
                "text": document,
                "metadata": metadata,
                "distance": distance,
            }
        )

    return found_docs
