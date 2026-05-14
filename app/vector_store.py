"""ChromaDB와 Ollama 임베딩을 다루는 파일입니다.

이 파일은 크게 두 가지 일을 합니다.
1. 문장이나 문서를 Ollama 임베딩 모델로 숫자 벡터로 바꿉니다.
2. ChromaDB에 문서 청크를 저장하거나 검색합니다.
"""

from typing import Any
import unicodedata

import chromadb
from chromadb.config import Settings
import httpx
import ollama

from app.config import CHROMA_DB_DIR, COLLECTION_NAME, EMBEDDING_MODEL, OLLAMA_HOST


def get_ollama_client() -> ollama.Client:
    """Ollama 서버와 통신하는 클라이언트를 만듭니다."""

    return ollama.Client(host=OLLAMA_HOST)


def get_chroma_client() -> chromadb.PersistentClient:
    """ChromaDB 클라이언트를 만듭니다.

    anonymized_telemetry=False는 ChromaDB의 사용 통계 전송을 끄는 설정입니다.
    이 설정을 꺼도 문서 저장과 검색 기능에는 영향을 주지 않습니다.
    """

    return chromadb.PersistentClient(
        path=str(CHROMA_DB_DIR),
        settings=Settings(anonymized_telemetry=False),
    )


def get_chroma_collection() -> Any:
    """ChromaDB 컬렉션을 가져옵니다.

    컬렉션은 문서 청크와 임베딩을 저장하는 공간입니다.
    컬렉션이 아직 없으면 자동으로 새로 만듭니다.
    """

    client = get_chroma_client()
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


def normalize_text(text: str) -> str:
    """검색 비교를 쉽게 하기 위해 텍스트를 정리합니다.

    macOS에서는 한글 파일명이나 문자열이 자모 분리 형태로 저장되는 경우가 있습니다.
    NFC 정규화를 하면 겉보기에는 같은데 내부 표현이 다른 한글도 비교하기 쉬워집니다.
    """

    return unicodedata.normalize("NFC", text).lower()


def keyword_search_documents(question: str, top_k: int) -> list[dict[str, Any]]:
    """질문 단어가 그대로 들어 있는 문서 청크를 찾습니다.

    교수명, 호실, 전화번호, 이메일처럼 정확한 문자열이 중요한 질문은
    벡터 검색보다 단순 키워드 검색이 더 잘 맞는 경우가 많습니다.
    """

    collection = get_chroma_collection()
    result = collection.get(include=["documents", "metadatas"])

    query = normalize_text(question.strip())
    if not query:
        return []

    # 짧은 질문은 전체 문장을 하나의 검색어로 쓰고,
    # 긴 질문은 2글자 이상 단어도 함께 확인합니다.
    query_terms = {query}
    query_terms.update(term for term in query.split() if len(term) >= 2)

    matches = []
    documents = result.get("documents", [])
    metadatas = result.get("metadatas", [])

    for document, metadata in zip(documents, metadatas):
        source = str(metadata.get("source", ""))
        searchable_text = normalize_text(f"{source}\n{document}")

        score = 0
        for term in query_terms:
            if term in searchable_text:
                # 전체 질문이 그대로 들어 있으면 가장 강한 근거로 봅니다.
                score += 10 if term == query else 3

        if score == 0:
            continue

        matches.append(
            {
                "text": document,
                "metadata": metadata,
                "distance": 0.0,
                "keyword_score": score,
            }
        )

    matches.sort(key=lambda item: item["keyword_score"], reverse=True)
    return matches[:top_k]


def merge_search_results(
    keyword_results: list[dict[str, Any]],
    vector_results: list[dict[str, Any]],
    top_k: int,
) -> list[dict[str, Any]]:
    """키워드 검색 결과와 벡터 검색 결과를 중복 없이 합칩니다."""

    merged = []
    seen = set()

    for result in keyword_results + vector_results:
        metadata = result["metadata"]
        key = (metadata.get("source"), metadata.get("chunk_index"))

        if key in seen:
            continue

        seen.add(key)
        merged.append(result)

        if len(merged) >= top_k:
            break

    return merged


def search_similar_documents(question: str, top_k: int) -> list[dict[str, Any]]:
    """질문과 관련 있는 문서 청크를 ChromaDB에서 찾습니다.

    먼저 정확한 이름이나 연락처를 잡기 위해 키워드 검색을 수행하고,
    그다음 의미가 비슷한 문서를 찾기 위해 벡터 검색을 함께 사용합니다.
    """

    collection = get_chroma_collection()
    keyword_results = keyword_search_documents(question=question, top_k=top_k)

    # 교수명, 이메일, 전화번호처럼 정확한 문자열이 문서에서 발견되면
    # 벡터 검색 결과를 섞지 않습니다. 벡터 검색은 의미상 비슷한 청크를 가져오지만,
    # 이름 검색에서는 엉뚱한 교수님 정보가 뒤에 붙어 답변을 흐릴 수 있습니다.
    if keyword_results:
        return keyword_results

    try:
        question_embedding = embed_text(question)
    except (httpx.HTTPError, ollama.ResponseError):
        # Ollama가 꺼져 있거나 연결할 수 없더라도,
        # 정확한 키워드로 찾은 결과가 있다면 점검용 검색에서는 확인할 수 있게 반환합니다.
        return keyword_results

    result = collection.query(
        query_embeddings=[question_embedding],
        n_results=top_k,
        include=["documents", "metadatas", "distances"],
    )

    documents = result.get("documents", [[]])[0]
    metadatas = result.get("metadatas", [[]])[0]
    distances = result.get("distances", [[]])[0]

    vector_results = []
    for document, metadata, distance in zip(documents, metadatas, distances):
        vector_results.append(
            {
                "text": document,
                "metadata": metadata,
                "distance": distance,
            }
        )

    return merge_search_results(
        keyword_results=keyword_results,
        vector_results=vector_results,
        top_k=top_k,
    )
