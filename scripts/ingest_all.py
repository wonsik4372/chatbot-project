"""TXT와 PDF 문서를 함께 읽어서 ChromaDB에 저장하는 스크립트입니다.

실행 명령어:
    python scripts/ingest_all.py

2단계부터는 이 스크립트를 기본 색인 명령으로 사용합니다.
TXT와 PDF를 같은 ChromaDB 컬렉션에 저장하되, metadata의 source_type으로 구분합니다.
"""

from pathlib import Path
import sys

# scripts 폴더에서 실행해도 app 패키지를 찾을 수 있도록 프로젝트 루트를 경로에 추가합니다.
PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.append(str(PROJECT_ROOT))

from app.config import (  # noqa: E402
    CHROMA_DB_DIR,
    PDF_DATA_DIR,
    TXT_DATA_DIR,
)
from app.chroma_store import rebuild_collection  # noqa: E402
from app.document_loader import collect_pdf_chunks, collect_txt_chunks, merge_document_chunks  # noqa: E402


def main() -> None:
    """TXT와 PDF 문서를 ChromaDB에 색인합니다."""

    TXT_DATA_DIR.mkdir(parents=True, exist_ok=True)
    PDF_DATA_DIR.mkdir(parents=True, exist_ok=True)

    txt_result = collect_txt_chunks()
    pdf_result = collect_pdf_chunks()
    ids, texts, metadatas = merge_document_chunks(txt_result, pdf_result)

    if not texts:
        print("색인할 TXT/PDF 파일이 없습니다. data/txt 또는 data/pdf 폴더에 파일을 넣어주세요.")
        return

    rebuild_collection(ids=ids, texts=texts, metadatas=metadatas)

    print(f"TXT/PDF 문서 색인 완료: {len(texts)}개 청크 저장")
    print(f"ChromaDB 저장 위치: {CHROMA_DB_DIR}")


if __name__ == "__main__":
    main()
