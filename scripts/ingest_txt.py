"""TXT 문서를 읽어서 ChromaDB에 저장하는 스크립트입니다.

실행 명령어:
    python scripts/ingest_txt.py

이 스크립트는 1단계에서 가장 먼저 실행해야 합니다.
문서를 벡터DB에 넣어야 챗봇이 검색할 수 있기 때문입니다.
"""

from pathlib import Path
import sys


# scripts 폴더에서 실행해도 app 패키지를 찾을 수 있도록 프로젝트 루트를 경로에 추가합니다.
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.append(str(PROJECT_ROOT))

from app.config import (  # noqa: E402
    CHROMA_DB_DIR,
    TXT_DATA_DIR,
)
from app.chroma_store import rebuild_collection  # noqa: E402
from app.document_loader import collect_txt_chunks  # noqa: E402


def main() -> None:
    """TXT 문서를 ChromaDB에 색인합니다."""

    TXT_DATA_DIR.mkdir(parents=True, exist_ok=True)

    ids, texts, metadatas = collect_txt_chunks()

    if not texts:
        print("색인할 TXT 파일이 없습니다. data/txt 폴더에 .txt 파일을 넣어주세요.")
        return

    rebuild_collection(ids=ids, texts=texts, metadatas=metadatas)

    print(f"TXT 문서 색인 완료: {len(texts)}개 청크 저장")
    print(f"ChromaDB 저장 위치: {CHROMA_DB_DIR}")


if __name__ == "__main__":
    main()
