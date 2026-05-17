package com.dongeui.rag.parser;

import com.dongeui.rag.model.KnowledgeCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.pdfbox.pdmodel.PDDocument;
import technology.tabula.*;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF → JSON 구조화
 * JSON → AI Markdown 렌더링
 */
public class PdfMarkdownParser {

    private static final String[] DAYS = {
            "월요일",
            "화요일",
            "수요일",
            "목요일",
            "금요일",
            "토요일"
    };

    /*
     * 교수명 사전
     */
    private static final List<String> PROFESSORS =
            List.of(
                    "권순각",
                    "김성우",
                    "권오준",
                    "이중화",
                    "이종민",
                    "박유현",
                    "빈기철",
                    "김용하",
                    "이유태",
                    "최병윤"
            );

    private final ObjectMapper mapper;

    /*
     * AI 컴파일러
     */
    private final KnowledgeCompiler compiler;

    public PdfMarkdownParser(
            KnowledgeCompiler compiler
    ) {

        this.compiler = compiler;

        this.mapper = new ObjectMapper();

        this.mapper.enable(
                SerializationFeature.INDENT_OUTPUT
        );
    }

    /**
     * 메인 진입
     */
    public String parse(
            File pdfFile
    ) throws Exception {

        TimetableDocument doc =
                extractDocument(pdfFile);

        /*
         * JSON 생성
         */
        String json =
                mapper.writeValueAsString(doc);

        /*
         * JSON 저장
         */
        saveJsonFile(
                pdfFile.getName(),
                json
        );

        /*
         * AI Markdown 렌더링
         */
        return compiler.compileTimetable(
                json,
                pdfFile.getName()
        );
    }

    /**
     * PDF 분석
     */
    private TimetableDocument extractDocument(
            File pdfFile
    ) throws Exception {

        TimetableDocument document =
                new TimetableDocument();

        document.year = 2026;
        document.semester = 1;
        document.source = pdfFile.getName();
        document.defaultRoom =
                extractRoom(pdfFile.getName());

        /*
         * 교수님 PDF 여부
         */
        String professorHint =
                extractProfessorFromFile(
                        pdfFile.getName()
                );

        try (PDDocument pdf =
                     PDDocument.load(pdfFile)) {

            ObjectExtractor extractor =
                    new ObjectExtractor(pdf);

            SpreadsheetExtractionAlgorithm spreadsheet =
                    new SpreadsheetExtractionAlgorithm();

            BasicExtractionAlgorithm basic =
                    new BasicExtractionAlgorithm();

            PageIterator pages =
                    extractor.extract();

            while (pages.hasNext()) {

                Page page =
                        pages.next();

                List<Table> tables =
                        spreadsheet.extract(page);

                /*
                 * fallback
                 */
                if (tables == null
                        || tables.isEmpty()) {

                    tables =
                            basic.extract(page);
                }

                for (Table table : tables) {

                    List<List<RectangularTextContainer>>
                            rows =
                            table.getRows();

                    for (List<RectangularTextContainer> row
                            : rows) {

                        /*
                         * 최소:
                         * 교시 + 월~토
                         */
                        if (row.size() < 7) {
                            continue;
                        }

                        String period =
                                clean(
                                        row.get(0)
                                                .getText()
                                );

                        if (!period.contains("교시")) {
                            continue;
                        }

                        /*
                         * 월~토 순회
                         */
                        for (int i = 1; i <= 6; i++) {

                            RectangularTextContainer cell =
                                    row.get(i);

                            if (cell == null) {
                                continue;
                            }

                            String raw =
                                    clean(
                                            cell.getText()
                                    );

                            if (raw.isBlank()) {
                                continue;
                            }

                            ScheduleItem item =
                                    parseCell(raw);

                            item.day =
                                    DAYS[i - 1];

                            item.period =
                                    period;

                            /*
                             * 교수님 PDF면
                             * 교수명 강제 주입
                             */
                            if (!professorHint.equals("미지정")) {

                                item.professor =
                                        professorHint;
                            }

                            /*
                             * 강의실 기본값
                             */
                            if (item.room == null
                                    || item.room.isBlank()) {

                                item.room =
                                        document.defaultRoom;
                            }

                            document.schedules.add(item);
                        }
                    }
                }
            }
        }

        return document;
    }

    /**
     * 셀 분석
     */
    private ScheduleItem parseCell(
            String raw
    ) {

        ScheduleItem item =
                new ScheduleItem();

        /*
         * 줄 정리
         */
        raw = raw
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        /*
         * 강의실 추출
         */
        Matcher roomMatcher =
                Pattern.compile("정보[- ]?\\d{3,4}")
                        .matcher(raw);

        if (roomMatcher.find()) {

            item.room =
                    roomMatcher.group()
                            .replace(" ", "-");

            raw =
                    raw.replace(
                            roomMatcher.group(),
                            ""
                    );
        }

        /*
         * 교수명 추출
         */
        for (String professor : PROFESSORS) {

            if (raw.contains(professor)) {

                item.professor =
                        professor;

                raw =
                        raw.replace(
                                professor,
                                ""
                        );

                break;
            }
        }

        /*
         * 과목명 정리
         */
        item.subject =
                normalizeSubject(raw);

        if (item.professor == null
                || item.professor.isBlank()) {

            item.professor =
                    "미지정";
        }

        return item;
    }

    /**
     * 과목명 복원
     */
    private String normalizeSubject(
            String s
    ) {

        return s

                .replace("(학)", "")
                .replace("(대)", "")

                .replace("그래픽스프로그래",
                        "그래픽스프로그래밍")

                .replace("디지털신호처리특",
                        "디지털신호처리특론")

                .replace("AI시대의사고와표",
                        "AI시대의사고와표현")

                .replace("프로젝트와기업가",
                        "프로젝트와기업가정신")

                .replace("ICT기반의창조경",
                        "ICT기반의창조경제")

                .replace("임베디드소프트웨",
                        "임베디드소프트웨어")

                .replace("지도교수멘토링Ⅶ",
                        "지도교수멘토링VII")

                .replace("캡스톤디자인Ⅱ",
                        "캡스톤디자인II")

                .replace("교과교육론(정보.",
                        "교과교육론(정보.컴퓨터)")

                .trim();
    }

    /**
     * 파일명 기반 강의실
     */
    private String extractRoom(
            String filename
    ) {

        Matcher matcher =
                Pattern.compile("\\d{3,4}")
                        .matcher(filename);

        if (matcher.find()) {

            return "정보-" + matcher.group();
        }

        return "미지정";
    }

    /**
     * 교수님 PDF 판별
     */
    private String extractProfessorFromFile(
            String filename
    ) {

        String name =
                filename
                        .replace(".pdf", "")
                        .replace("교수님", "")
                        .replace("교수", "")
                        .trim();

        /*
         * 숫자 파일이면 강의실 PDF
         */
        if (name.matches(".*\\d+.*")) {

            return "미지정";
        }

        for (String professor : PROFESSORS) {

            if (name.contains(professor)) {

                return professor;
            }
        }

        return "미지정";
    }

    /**
     * 문자열 정리
     */
    private String clean(
            String text
    ) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * JSON 저장
     */
    private void saveJsonFile(
            String pdfName,
            String json
    ) {

        try {

            String fileName =
                    pdfName.replace(".pdf", ".json");

            Path path =
                    Path.of(
                            "parsed-json",
                            fileName
                    );

            Files.createDirectories(
                    path.getParent()
            );

            Files.writeString(path, json);

            System.out.println(
                    "✅ JSON 저장 완료 -> "
                            + path
            );

        } catch (Exception e) {

            System.out.println(
                    "JSON 저장 실패: "
                            + e.getMessage()
            );
        }
    }

    /*
     * JSON ROOT
     */
    public static class TimetableDocument {

        public int year;

        public int semester;

        public String source;

        public String defaultRoom;

        public List<ScheduleItem> schedules =
                new ArrayList<>();
    }

    /*
     * 수업 DTO
     */
    public static class ScheduleItem {

        public String day;

        public String period;

        public String subject;

        public String professor;

        public String room;
    }
}