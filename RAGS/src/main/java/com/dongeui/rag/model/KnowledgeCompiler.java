package com.dongeui.rag.model;

import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KnowledgeCompiler {

    private final ChatLanguageModel chatModel;

    public KnowledgeCompiler(
            ChatLanguageModel chatModel
    ) {
        this.chatModel = chatModel;
    }

    /**
     * JSON → Markdown
     */
    public String compileTimetable(
            String jsonData,
            String sourceName
    ) {

        System.out.println(
                "✨ 시간표 Markdown 렌더링 중 -> "
                        + sourceName
        );

        /*
         * 파일명이 교수님 PDF인지 판별
         *
         * 예:
         * 빈기철교수님.pdf
         */
        String professorHint =
                extractProfessorFromFilename(
                        sourceName
                );

        boolean professorPdf =
                !professorHint.equals("미지정");

        /*
         * 파일명 기반 강의실 추론
         *
         * 911.pdf -> 정보-911
         */
        String roomHint =
                extractRoomFromFilename(
                        sourceName
                );

        /*
         * 교수님 PDF 규칙
         */
        String typeRule;

        if (professorPdf) {

            typeRule = """
                    [교수님 PDF 규칙]

                    이 PDF는 교수님 개인 시간표이다.

                    교수명은 무조건:
                    %s

                    JSON 내부에 교수명이 없어도
                    모든 수업의 교수명을 반드시
                    "%s" 로 채워라.

                    절대 "미지정" 사용 금지.
                    """
                    .formatted(
                            professorHint,
                            professorHint
                    );

        } else {

            typeRule = """
                    [강의실 PDF 규칙]

                    이 PDF는 강의실 시간표이다.

                    JSON 내부에 교수명이 없으면:
                    "미지정" 사용.

                    강의실은 기본적으로:
                    %s
                    """
                    .formatted(roomHint);
        }

        /*
         * 메인 프롬프트
         */
        String prompt = """
                너는 대학 시간표 JSON 데이터를
                Markdown으로 변환하는 시스템이다.

                %s

                --------------------------------
                [절대 규칙]
                --------------------------------

                1.
                같은 요일끼리 묶어라.

                2.
                반드시 아래 형식으로만 출력:

                - 1교시: 과목명 / 교수명 / 강의실

                3.
                설명 금지

                4.
                요약 금지

                5.
                JSON 데이터를 누락하지 마라.

                6.
                출력은 반드시 Markdown만 출력한다.

                7.
                잘린 과목명 자동 복원:

                - 그래픽스프로그래
                  → 그래픽스프로그래밍

                - 지도교수멘토링Ⅶ
                  → 지도교수멘토링VII

                - 캡스톤디자인Ⅱ
                  → 캡스톤디자인II

                - AI시대의사고와표
                  → AI시대의사고와표현

                - 디지털신호처리특
                  → 디지털신호처리특론

                - 프로젝트와기업가
                  → 프로젝트와기업가정신

                - ICT기반의창조경
                  → ICT기반의창조경제

                - 임베디드소프트웨
                  → 임베디드소프트웨어

                - 교과교육론(정보.
                  → 교과교육론(정보.컴퓨터)

                - 소프트웨어공학(키
                  → 소프트웨어공학(키트)

                8.
                아래 단어는 교수명이 아니다:

                - 정보
                - 강의실
                - 융합소
                - 프로그래밍
                - 캡스톤
                - 데이터
                - 컴퓨터
                - 프로젝트
                - 논문지도
                - 지도교수
                - AI프로그래밍

                9.
                정보-911 같은 문자열은
                강의실이다.

                10.
                3~4글자 한글 이름은
                교수명일 가능성이 높다.

                --------------------------------
                [출력 예시]
                --------------------------------

                # 📘 2026학년도 1학기 시간표

                데이터 출처: 911.pdf

                ## 월요일

                - 1교시: 운영체제 / 김성우 / 정보-911
                - 2교시: 데이터구조 / 이중화 / 정보-911

                ## 화요일

                - 3교시: 컴퓨터네트워크 / 빈기철 / 정보-911

                --------------------------------
                [입력 JSON]
                --------------------------------

                %s

                --------------------------------
                [최종 Markdown]
                --------------------------------
                """
                .formatted(
                        typeRule,
                        jsonData
                );

        return executeQuery(
                prompt,
                jsonData
        );
    }


    /**
     * 2. 웹 크롤링 데이터 전용 컴파일러 (검색 최적화 태그 주입형)
     */
    public String compileWebContent(String rawContent, String sourceName) {
        System.out.println("✨ LLM이 [" + sourceName + "] 웹 크롤링 데이터를 RAG 최적화 마크다운으로 컴파일 중...");
        
        String prompt = String.format(
            "너는 학과 홈페이지에서 크롤링된 로우 텍스트를 RAG 검색에 완벽하게 적합한 구조화된 지식 문서로 정제하는 에이전트야.\n" +
            "인사말이나 사족 없이 오직 정제된 마크다운 결과물만 곧바로 출력해줘.\n\n" +
            
            "🧱 [작성 규칙]\n" +
            "1. 문서 최상단에 이 문서의 주제와 관련된 '검색용 핵심 키워드 태그'를 대량으로 배치해라.\n" +
            "   (예: 만약 교수 소개 페이지라면 #교수명단 #교수진 #교수님 #담당교수 #컴소교수 #교수소개 등을 무조건 포함)\n" +
            "2. 내용을 요약하여 생략하지 말고, 인물이나 항목이 등장하면 이름, 연구실, 연락처를 불릿 포인트로 모두 나열해라.\n\n" +
            
            "🧱 [출력 포맷 스키마]\n" +
            "## 🌐 학과 홈페이지 지식 베이스: [페이지 주제 명칭]\n" +
            "* **검색 태그:** #교수명단 #교수진 #전체교수 #교수소개 #연락처 #연구실 #컴퓨터소프트웨어공학과\n" +
            "* **문서 유형:** 학과 구성원 및 안내 정보 문서\n\n" +
            "### 📌 [주요 내용 및 리스트]\n" +
            "[입력 데이터의 내용을 기반으로 가독성 있게 마크다운화]\n\n" +
            "--- \n\n" +
            "[입력 데이터]\n" +
            "%s\n\n" +
            "마크다운 결과 >",
            rawContent
        );

        return executeQuery(prompt, rawContent);
    }

    private String extractProfessorFromFilename(
            String sourceName
    ) {

        String fileName =
                sourceName
                        .replace(".pdf", "")
                        .trim();

        /*
         * 숫자 포함이면 강의실 PDF
         */
        if (fileName.matches(".*\\d+.*")) {

            return "미지정";
        }

        fileName =
                fileName
                        .replace("교수님", "")
                        .replace("교수", "")
                        .trim();

        Matcher matcher =
                Pattern.compile("[가-힣]{3,4}")
                        .matcher(fileName);

        if (matcher.find()) {

            return matcher.group();
        }

        return "미지정";
    }

    /**
     * 파일명 기반 강의실 추론
     *
     * 예:
     * 911.pdf
     * -> 정보-911
     */
    private String extractRoomFromFilename(
            String sourceName
    ) {

        Matcher matcher =
                Pattern.compile("(\\d{3,4})")
                        .matcher(sourceName);

        if (matcher.find()) {

            return "정보-" + matcher.group(1);
        }

        return "미지정";
    }

    private String executeQuery(String prompt, String fallback) {
        try {
            return this.chatModel.generate(prompt);
        } catch (Exception e) {
            System.err.println("⚠️ LLM 컴파일 프롬프트 전송 실패로 폴백 처리합니다: " + e.getMessage());
            return fallback;
        }
    }
}