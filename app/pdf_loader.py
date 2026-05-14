"""PDF 파일에서 텍스트를 추출하는 기능을 모아둔 파일입니다.

일반 PDF는 페이지 텍스트를 그대로 추출합니다.
시간표 PDF는 표 형태라서 일반 텍스트 추출만으로는 요일과 교시 관계가 깨집니다.
그래서 시간표는 좌표를 이용해 구조화된 문장으로 변환합니다.
"""

from pathlib import Path
import re

from pypdf import PdfReader


DAYS = ["월", "화", "수", "목", "금", "토"]
DAY_NAMES = {
    "월": "월요일",
    "화": "화요일",
    "수": "수요일",
    "목": "목요일",
    "금": "금요일",
    "토": "토요일",
}


def clean_schedule_text(text: str) -> str:
    """시간표에서 추출한 과목명/교수명 텍스트를 정리합니다."""

    cleaned = text.strip()
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned


def clean_schedule_subject(subject: str) -> str:
    """시간표 과목명에서 범례와 잘림 표시를 제거합니다.

    PDF 표에는 학부/대학원 구분을 나타내는 (학), (대) 같은 표기가 붙습니다.
    또한 칸이 좁아 과목명이 잘릴 때 "(키" 같은 잔여 문자가 붙는 경우가 있습니다.
    이 함수는 답변에 불필요한 표기를 줄이기 위해 그런 값을 제거합니다.
    """

    cleaned = clean_schedule_text(subject)

    # 학부/대학원/행정 등 범례 표기를 제거합니다.
    cleaned = re.sub(r"^\((학|대|행|경|산|교|중|영)\)", "", cleaned)

    # 과목명 뒤에 붙은 잘림 표시를 제거합니다.
    cleaned = re.sub(r"\(키$", "", cleaned)
    cleaned = re.sub(r"\s+", " ", cleaned)

    return cleaned.strip()


def clean_schedule_teacher(teacher: str) -> str:
    """시간표 담당교수 텍스트를 정리합니다."""

    return clean_schedule_text(teacher)


def clean_schedule_room(room: str) -> str:
    """교수님 시간표에서 추출한 강의실 표기를 정리합니다."""

    cleaned = clean_schedule_text(room)
    cleaned = re.sub(r"정보-(\d{3})", r"정보공학관 \1호 ", cleaned)
    cleaned = cleaned.replace("프로그", "프로그래밍언어실습실")
    cleaned = re.sub(r"\s+", " ", cleaned)
    return cleaned.strip()


def read_pdf_pages(path: Path) -> list[tuple[int, str]]:
    """일반 PDF 파일을 페이지별 텍스트 목록으로 반환합니다."""

    reader = PdfReader(str(path))
    pages = []

    for page_index, page in enumerate(reader.pages, start=1):
        text = page.extract_text() or ""
        text = text.strip()

        if text:
            pages.append((page_index, text))

    return pages


def is_schedule_pdf(path: Path) -> bool:
    """파일명에 '시간표'가 들어 있는 PDF인지 확인합니다."""

    return "시간표" in path.stem


def is_room_schedule_pdf(path: Path) -> bool:
    """강의실별 시간표 PDF인지 확인합니다."""

    return "강의실" in path.stem and is_schedule_pdf(path)


def is_professor_schedule_pdf(path: Path) -> bool:
    """교수님별 시간표 PDF인지 확인합니다."""

    return "교수님" in path.stem and is_schedule_pdf(path)


def collect_positioned_text(page) -> list[tuple[float, float, str]]:
    """PDF 페이지에서 글자와 좌표를 함께 추출합니다."""

    items = []

    def visitor(text, cm, tm, font_dict, font_size):
        clean_text = text.strip()
        if not clean_text:
            return

        x = float(tm[4])
        y = float(tm[5])
        items.append((x, y, clean_text))

    page.extract_text(visitor_text=visitor)
    return items


def join_text(items: list[tuple[float, float, str]]) -> str:
    """좌표순으로 정렬된 글자 조각을 하나의 문자열로 합칩니다."""

    return "".join(text for _, _, text in sorted(items, key=lambda item: item[0]))


def get_text_near_y(
    items: list[tuple[float, float, str]],
    target_y: float,
    min_x: float,
    max_x: float,
    tolerance: float = 2.0,
) -> str:
    """특정 y좌표와 x범위 안에 있는 텍스트를 읽습니다."""

    matched = [
        (x, y, text)
        for x, y, text in items
        if abs(y - target_y) <= tolerance and min_x <= x < max_x
    ]
    return join_text(matched)


def find_day_columns(items: list[tuple[float, float, str]]) -> dict[str, float]:
    """시간표의 요일 헤더 위치를 찾습니다."""

    columns = {}

    for x, y, text in items:
        if text in DAYS and 490 <= y <= 505:
            columns[text] = x

    return columns


def make_column_ranges(day_columns: dict[str, float]) -> dict[str, tuple[float, float]]:
    """요일별 x좌표 범위를 계산합니다."""

    ordered = [(day, day_columns[day]) for day in DAYS if day in day_columns]
    ranges = {}

    for index, (day, center_x) in enumerate(ordered):
        if index == 0:
            left = center_x - 55
        else:
            left = (ordered[index - 1][1] + center_x) / 2

        if index == len(ordered) - 1:
            right = center_x + 55
        else:
            right = (center_x + ordered[index + 1][1]) / 2

        ranges[day] = (left, right)

    return ranges


def find_period_rows(items: list[tuple[float, float, str]]) -> list[tuple[int, float, str]]:
    """시간표 왼쪽의 교시와 시간 정보를 찾습니다."""

    rows = []

    for x, y, text in items:
        if not (40 <= x <= 120):
            continue

        # 같은 y좌표에 있는 왼쪽 영역 텍스트를 합쳐서 "3교시" 같은 값을 찾습니다.
        row_text = get_text_near_y(items, target_y=y, min_x=40, max_x=120, tolerance=1.0)
        match = re.fullmatch(r"(\d+)교시", row_text)
        if not match:
            continue

        period = int(match.group(1))
        time_text = get_text_near_y(items, target_y=y - 9, min_x=40, max_x=125, tolerance=2.0)
        rows.append((period, y, time_text))

    # 같은 교시가 여러 글자 때문에 중복 발견될 수 있어 y좌표 기준으로 중복 제거합니다.
    unique_rows = []
    seen_y = set()

    for period, y, time_text in sorted(rows, key=lambda row: -row[1]):
        rounded_y = round(y, 1)
        if rounded_y in seen_y:
            continue

        seen_y.add(rounded_y)
        unique_rows.append((period, y, time_text))

    return unique_rows


def find_professor_period_rows(items: list[tuple[float, float, str]]) -> list[tuple[int, float, str]]:
    """교수님 시간표 왼쪽의 교시와 시간 정보를 찾습니다."""

    rows = []

    for x, y, text in items:
        if not (400 <= x <= 900):
            continue

        row_text = get_text_near_y(items, target_y=y, min_x=400, max_x=900, tolerance=2.0)
        match = re.fullmatch(r"(\d+)교시", row_text)
        if not match:
            continue

        period = int(match.group(1))
        time_text = get_text_near_y(items, target_y=y + 75, min_x=400, max_x=900, tolerance=4.0)
        rows.append((period, y, time_text))

    unique_rows = []
    seen_y = set()

    for period, y, time_text in sorted(rows, key=lambda row: -row[1]):
        rounded_y = round(y, 1)
        if rounded_y in seen_y:
            continue

        seen_y.add(rounded_y)
        unique_rows.append((period, y, time_text))

    return unique_rows


def read_room_schedule_pages(path: Path) -> list[tuple[int, str]]:
    """강의실 시간표 PDF를 구조화된 문장으로 변환합니다."""

    reader = PdfReader(str(path))
    pages = []

    for page_index, page in enumerate(reader.pages, start=1):
        items = collect_positioned_text(page)
        day_columns = find_day_columns(items)

        if not day_columns:
            fallback_text = page.extract_text() or ""
            if fallback_text.strip():
                pages.append((page_index, fallback_text.strip()))
            continue

        column_ranges = make_column_ranges(day_columns)
        period_rows = find_period_rows(items)
        room_title = get_text_near_y(items, target_y=525.4, min_x=35, max_x=300, tolerance=4.0)
        room_match = re.search(r"(\d{3})", room_title or path.stem)
        room_number = room_match.group(1) if room_match else path.stem
        room_name = re.sub(rf"정보공학관\s*{room_number}", "", room_title).strip()
        room_label = f"정보공학관 {room_number}호"

        if room_name:
            room_label = f"{room_label} {room_name}"

        sentences = []

        for period, period_y, time_text in period_rows:
            subject_y = period_y + 2.4
            teacher_y = period_y - 10.9

            for day, (min_x, max_x) in column_ranges.items():
                subject = get_text_near_y(items, subject_y, min_x, max_x)
                teacher = get_text_near_y(items, teacher_y, min_x, max_x)
                subject = clean_schedule_subject(subject)
                teacher = clean_schedule_teacher(teacher)

                if not subject:
                    continue

                sentence = (
                    f"{room_label} "
                    f"{DAY_NAMES[day]} {period}교시 {time_text}: "
                    f"{subject}"
                )

                if teacher:
                    sentence += f", 담당교수 {teacher}"

                sentences.append(sentence + ".")

        if sentences:
            pages.append((page_index, "\n".join(sentences)))

    return pages


def read_professor_schedule_pages(path: Path) -> list[tuple[int, str]]:
    """교수님별 시간표 PDF를 구조화된 문장으로 변환합니다."""

    reader = PdfReader(str(path))
    pages = []
    professor_name = path.stem.replace("교수님 시간표", "").strip()

    # 교수님별 시간표 PDF는 강의실별 시간표와 좌표 단위가 다릅니다.
    # 현재 PDF 양식 기준으로 요일별 열 범위를 고정합니다.
    column_centers = {
        "월": 1498.0,
        "화": 2424.0,
        "수": 3350.0,
        "목": 4276.0,
        "금": 5202.0,
        "토": 6128.0,
    }
    column_ranges = make_column_ranges(column_centers)

    for page_index, page in enumerate(reader.pages, start=1):
        items = collect_positioned_text(page)
        period_rows = find_professor_period_rows(items)
        sentences = []

        for period, period_y, time_text in period_rows:
            subject_y = period_y - 22
            room_y = period_y - 134

            for day, (min_x, max_x) in column_ranges.items():
                subject = get_text_near_y(items, subject_y, min_x, max_x, tolerance=4.0)
                room = get_text_near_y(items, room_y, min_x, max_x, tolerance=4.0)
                subject = clean_schedule_subject(subject)
                room = clean_schedule_room(room)

                if not subject:
                    continue

                sentence = (
                    f"{professor_name} 교수님 "
                    f"{DAY_NAMES[day]} {period}교시 {time_text}: "
                    f"{subject}"
                )

                if room:
                    sentence += f", 강의실 {room}"

                sentences.append(sentence + ".")

        if sentences:
            pages.append((page_index, "\n".join(sentences)))

    if pages:
        return pages

    # 교수님 시간표 PDF 양식이 다르게 추출되면 구조화가 실패할 수 있습니다.
    # 이 경우 색인 누락을 막기 위해 일반 PDF 텍스트 추출 결과를 사용합니다.
    return read_pdf_pages(path)


def read_pdf_for_rag(path: Path) -> list[tuple[int, str]]:
    """RAG 색인에 사용할 PDF 텍스트를 반환합니다."""

    if is_room_schedule_pdf(path):
        return read_room_schedule_pages(path)

    if is_professor_schedule_pdf(path):
        return read_professor_schedule_pages(path)

    return read_pdf_pages(path)
