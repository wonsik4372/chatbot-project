package com.dongeui.rag.router;

public class QueryRouter {

    public enum QueryType {
        TIMETABLE,
        PROFESSOR,
        WEB,
        GENERAL
    }

    public QueryType route(String query) {

        query = query.toLowerCase();

        if (query.contains("시간표")
                || query.contains("강의실")
                || query.contains("공강")
                || query.contains("교시")) {

            return QueryType.TIMETABLE;
        }

        if (query.contains("교수")
                || query.contains("연구실")
                || query.contains("이메일")) {

            return QueryType.PROFESSOR;
        }

        if (query.contains("취업")
                || query.contains("학과")
                || query.contains("동아리")
                || query.contains("공지")) {

            return QueryType.WEB;
        }

        return QueryType.GENERAL;
    }
}