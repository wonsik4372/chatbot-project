package com.dongeui.rag.router;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryClassifier {

    private static final Pattern ROOM_PATTERN =
            Pattern.compile("(9\\d{2})");

    public QueryAnalysisResult analyze(String query) {

        QueryAnalysisResult result =
                new QueryAnalysisResult();

        result.setOriginalQuery(query);

        query = normalize(query);

        // 교수 명단
        if ((query.contains("교수")
                || query.contains("교수님"))
                &&
                (query.contains("명단")
                        || query.contains("전체")
                        || query.contains("누가"))) {

            result.setType(QueryType.PROFESSOR);

            result.setProfessor(null);

            return result;
        }

        // 교수 소개
        if ((query.contains("교수")
                || query.contains("교수님"))
                &&
                (query.contains("소개")
                        || query.contains("약력")
                        || query.contains("전공")
                        || query.contains("연구"))) {

            result.setType(QueryType.PROFESSOR);

            return result;
        }

        // 강의실 질문
        Matcher roomMatcher =
                ROOM_PATTERN.matcher(query);

        if (roomMatcher.find()) {

            String room =
                    roomMatcher.group(1);

            result.setType(QueryType.ROOM);

            result.setRoom(
                    "정보-" + room
            );

            return result;
        }

        // 교수 질문
        if (query.contains("교수")
                || query.contains("교수님")) {

            result.setType(QueryType.PROFESSOR);

            String professor =
                    extractProfessorName(query);

            result.setProfessor(professor);

            return result;
        }

        // 공강
        if (query.contains("공강")
                || query.contains("빈 강의실")
                || query.contains("없는 강의실")) {

            result.setType(QueryType.TIMETABLE);

            return result;
        }

        // 시간표
        if (query.contains("시간표")
                || query.contains("교시")) {

            result.setType(QueryType.TIMETABLE);

            return result;
        }

        // 웹 질문
        if (query.contains("공지")
                || query.contains("취업")
                || query.contains("동아리")
                || query.contains("학과")) {

            result.setType(QueryType.WEB);

            return result;
        }

        result.setType(QueryType.GENERAL);

        return result;
    }

    private String normalize(String query) {

        query = query.replace("914호", "914");
        query = query.replace("915호", "915");
        query = query.replace("916호", "916");
        query = query.replace("917호", "917");
        query = query.replace("918호", "918");

        return query;
    }

    private String extractProfessorName(
            String query
    ) {

        query = query.replace("교수님", "");
        query = query.replace("교수", "");

        query = query.replace("수업", "");
        query = query.replace("시간표", "");

        query = query.trim();

        String[] tokens =
                query.split("\\s+");

        for (String token : tokens) {

            token = token.trim();

            if (token.length() >= 2
                    && !token.equals("학과")
                    && !token.equals("수업")
                    && !token.equals("시간표")) {

                return token;
            }
        }

        return "";
    }
}