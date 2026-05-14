package com.mycompany.chatbot.rag.project;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.data.segment.TextSegment;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class RAGAssistant {

    private static final String OLLAMA_BASE_URL
            = "http://localhost:11434";

    private static final String CHAT_MODEL = "gemma4";

    private final ChatLanguageModel chatModel;
    private final VectorStoreManager vectorStore;
    private final TimetableStore timetableStore;

    public RAGAssistant(
            VectorStoreManager vectorStore,
            TimetableStore timetableStore
    ) {
        this.vectorStore = vectorStore;
        this.timetableStore = timetableStore;

        this.chatModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_BASE_URL)
                .modelName(CHAT_MODEL)
                .temperature(0.0)
                .timeout(Duration.ofMinutes(3))
                .build();
    }

    public String answer(String userQuery) {

        // 시간표 우선 처리
        if (isTimetableQuery(userQuery)) {
            String timetable = handleTimetableQuery(userQuery);

            if (timetable != null) {
                return timetable;
            }
        }

        List<EmbeddingMatch<TextSegment>> matches;

        if (userQuery.contains("약사") || userQuery.contains("연혁")) {
            matches = vectorStore.retrieveBySource(
                    userQuery,
                    source -> source.contains("sub01_02")
            );

        } else if (userQuery.contains("교육과정")
                || userQuery.contains("교과목")
                || userQuery.contains("전문트랙")
                || userQuery.contains("이수표")) {
            matches = vectorStore.retrieveBySource(
                    userQuery,
                    source -> source.contains("sub03")
            );

        } else if ((userQuery.contains("교수")
                && (userQuery.contains("소개")
                || userQuery.contains("연구")
                || userQuery.contains("이메일")
                || userQuery.contains("연구실")
                || userQuery.contains("강의분야")
                || userQuery.contains("홈페이지")))
                || userQuery.contains("교수진")
                || userQuery.contains("명단")) {

            matches = vectorStore.retrieveBySource(
                    userQuery,
                    source -> source.contains("sub02")
                    || source.contains("compnet.deu.ac.kr")
            );

        } else if (userQuery.contains("취업")) {
            matches = vectorStore.retrieveBySource(
                    userQuery,
                    source -> source.contains("sub05")
            );

        } else {
            matches = vectorStore.retrieve(userQuery);
        }

        if (matches.isEmpty()) {
            return "인덱싱된 문서에서 관련 내용을 찾을 수 없습니다.";
        }

        List<String> contexts = matches.stream()
                .map(m -> m.embedded().text())
                .toList();

        String prompt = buildPrompt(contexts, userQuery);

        return chatModel.generate(prompt);
    }

    private boolean isTimetableQuery(String query) {
        return query.contains("시간표")
                || query.contains("강의 시간")
                || query.contains("강의 목록")
                || query.contains("강의")
                || query.contains("교과목")
                || query.contains("호실");
    }

    private String handleTimetableQuery(String query) {

        Pattern professorPattern
                = Pattern.compile("([가-힣]{2,4})(?=\\s*교수)");

        Matcher professorMatcher
                = professorPattern.matcher(query);

        if (professorMatcher.find()) {
            String professor = professorMatcher.group(1);

            Timetable timetable
                    = timetableStore.getProfessorSchedule(professor);

            if (timetable != null) {

                if (query.contains("교과목")
                        && !query.contains("시간")
                        && !query.contains("시간표")) {
                    return timetable.getCourseList();
                }

                return filterByDay(query, timetable);
            }
        }

        Pattern roomPattern
                = Pattern.compile("(9\\d{2})");

        Matcher roomMatcher
                = roomPattern.matcher(query);

        if (roomMatcher.find()) {
            String room = roomMatcher.group(1);

            Timetable timetable
                    = timetableStore.getClassroomSchedule(room);

            if (timetable != null) {

                if (query.contains("교과목")
                        && !query.contains("시간")
                        && !query.contains("시간표")) {
                    return timetable.getCourseList();
                }

                return filterByDay(query, timetable);
            }
        }

        return null;
    }

    private String filterByDay(String query, Timetable timetable) {

        if (query.contains("월요일")) {
            return timetable.getDaySchedule("월");
        }
        if (query.contains("화요일")) {
            return timetable.getDaySchedule("화");
        }
        if (query.contains("수요일")) {
            return timetable.getDaySchedule("수");
        }
        if (query.contains("목요일")) {
            return timetable.getDaySchedule("목");
        }
        if (query.contains("금요일")) {
            return timetable.getDaySchedule("금");
        }

        return timetable.toMarkdown();
    }

    private String buildPrompt(List<String> contexts, String query) {
        String contextBlock = String.join("\n\n---\n\n", contexts);

        return """
               당신은 동의대학교 컴퓨터소프트웨어공학과 정보 안내 AI입니다.
                
               규칙:
               1. 아래 문맥에 있는 정보만 사용하세요.
               2. 문맥에 정보가 있으면 적극적으로 정리해서 답하세요.
               3. 여러 문맥에 흩어진 정보는 합쳐도 됩니다.
               4. 문맥에 정말 없을 때만:
                  "제공된 문서에는 해당 정보가 없습니다."
               5. 한국어로 답하세요.
               6. 문맥에 있는 정보는 그대로 추출/정리해서 답하세요.
               
               질문이 교수 소개면:
               - 이름
               - 연구분야
               - 연구실
               - 이메일
               - 홈페이지
               - 연락처
               를 우선 정리하고, 자세한 내용이 있다면 추가적으로 자세히 작성하세요.
               
               질문이 교과목이면:
               교과목명을 목록 형태로 정리하세요.
                
               [문맥]
               %s
                
               [질문]
               %s
                
               [답변]
               """.formatted(contextBlock, query);
    }
}
