"""TXT 문서를 읽어서 ChromaDB에 저장하는 스크립트입니다.

실행 명령어:
    python scripts/ingest_txt.py

이 스크립트는 1단계에서 가장 먼저 실행해야 합니다.
문서를 벡터DB에 넣어야 챗봇이 검색할 수 있기 때문입니다.
"""

from pathlib import Path
import sys
from uuid import uuid4

import chromadb


# scripts 폴더에서 실행해도 app 패키지를 찾을 수 있도록 프로젝트 루트를 경로에 추가합니다.
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.append(str(PROJECT_ROOT))

from app.config import (  # noqa: E402
    CHROMA_DB_DIR,
    COLLECTION_NAME,
    TXT_CHUNK_OVERLAP,
    TXT_CHUNK_SIZE,
    TXT_DATA_DIR,
)
from app.vector_store import add_documents  # noqa: E402


def read_txt_file(path: Path) -> str:
    """TXT 파일 하나를 읽어서 문자열로 반환합니다."""

    return path.read_text(encoding="utf-8")


def split_text(text: str, chunk_size: int, chunk_overlap: int) -> list[str]:
    """긴 텍스트를 일정한 크기의 청크로 나눕니다.

    chunk_size는 청크 하나의 최대 글자 수입니다.
    chunk_overlap은 앞 청크와 다음 청크가 겹치는 글자 수입니다.
    겹침을 주면 문맥이 중간에 끊기는 문제를 조금 줄일 수 있습니다.
    """

    chunks = []
    start = 0

    while start < len(text):
        end = start + chunk_size
        chunk = text[start:end].strip()

        if chunk:
            chunks.append(chunk)

        start = end - chunk_overlap

        # chunk_overlap 값이 너무 커서 무한 반복되는 상황을 막습니다.
        if start < 0:
            start = 0

    return chunks


def reset_collection() -> None:
    """기존 ChromaDB 컬렉션을 삭제하고 새로 만들 준비를 합니다.

    1단계에서는 단순함을 위해 색인할 때마다 전체 TXT 문서를 다시 저장합니다.
    """

    client = chromadb.PersistentClient(path=str(CHROMA_DB_DIR))

    try:
        client.delete_collection(name=COLLECTION_NAME)
    except ValueError:
        # 컬렉션이 아직 없으면 삭제할 것도 없으므로 그냥 넘어갑니다.
        pass

    client.get_or_create_collection(name=COLLECTION_NAME)


def collect_txt_chunks() -> tuple[list[str], list[str], list[dict[str, str | int]]]:
    """data/txt 폴더의 모든 TXT 파일을 읽고 청크 목록을 만듭니다."""

    ids = []
    texts = []
    metadatas = []

    txt_files = sorted(TXT_DATA_DIR.glob("*.txt"))

    for file_path in txt_files:
        text = read_txt_file(file_path)
        chunks = split_text(
            text=text,
            chunk_size=TXT_CHUNK_SIZE,
            chunk_overlap=TXT_CHUNK_OVERLAP,
        )

        for chunk_index, chunk in enumerate(chunks):
            ids.append(str(uuid4()))
            texts.append(chunk)
            metadatas.append(
                {
                    "source": str(file_path.relative_to(PROJECT_ROOT)),
                    "source_type": "txt",
                    "chunk_index": chunk_index,
                }
            )

    return ids, texts, metadatas


def main() -> None:
    """TXT 문서를 ChromaDB에 색인합니다."""

    TXT_DATA_DIR.mkdir(parents=True, exist_ok=True)
    reset_collection()

    ids, texts, metadatas = collect_txt_chunks()

    if not texts:
        print("색인할 TXT 파일이 없습니다. data/txt 폴더에 .txt 파일을 넣어주세요.")
        return

    add_documents(ids=ids, texts=texts, metadatas=metadatas)

    print(f"TXT 문서 색인 완료: {len(texts)}개 청크 저장")
    print(f"ChromaDB 저장 위치: {CHROMA_DB_DIR}")


if __name__ == "__main__":
    main()
