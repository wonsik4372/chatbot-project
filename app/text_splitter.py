"""문서를 청크로 나누는 기능을 모아둔 파일입니다.

현재 1단계에서는 TXT만 사용하지만, 2단계 PDF와 3단계 웹페이지에서도
같은 함수를 재사용할 수 있도록 별도 파일로 분리했습니다.
"""


def split_text(text: str, chunk_size: int, chunk_overlap: int) -> list[str]:
    """긴 텍스트를 일정한 크기의 청크로 나눕니다.

    chunk_size는 청크 하나의 최대 글자 수입니다.
    chunk_overlap은 앞 청크와 다음 청크가 겹치는 글자 수입니다.
    """

    if chunk_size <= 0:
        raise ValueError("chunk_size는 1 이상이어야 합니다.")

    if chunk_overlap < 0:
        raise ValueError("chunk_overlap은 0 이상이어야 합니다.")

    if chunk_overlap >= chunk_size:
        raise ValueError("chunk_overlap은 chunk_size보다 작아야 합니다.")

    chunks = []
    start = 0

    while start < len(text):
        end = start + chunk_size
        raw_chunk = text[start:end]
        chunk = raw_chunk.strip()

        if chunk:
            chunks.append(chunk)

        # 실제로 잘라낸 원문 길이를 기준으로 다음 시작점을 계산합니다.
        actual_cut_length = len(raw_chunk)
        next_start = start + actual_cut_length - chunk_overlap

        if actual_cut_length < chunk_size or next_start <= start:
            break

        start = next_start

    return chunks
