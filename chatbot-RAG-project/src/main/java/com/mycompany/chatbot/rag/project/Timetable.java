package com.mycompany.chatbot.rag.project;

public class Timetable {

    private final String ownerName;
    private final String[][] table; // [교시][요일]

    private static final String[] WEEKDAYS = {
        "월", "화", "수", "목", "금"
    };

    private static final String[] PERIODS = {
        "1교시", "2교시", "3교시", "4교시", "5교시",
        "6교시", "7교시", "8교시", "9교시"
    };

    public Timetable(String ownerName) {
        this.ownerName = ownerName;
        this.table = new String[9][5];

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 5; j++) {
                table[i][j] = "";
            }
        }
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setCourse(int periodIndex, int dayIndex, String course) {
        if (periodIndex < 0 || periodIndex >= 9) {
            return;
        }
        if (dayIndex < 0 || dayIndex >= 5) {
            return;
        }

        table[periodIndex][dayIndex] = course;
    }

    public String toMarkdown() {
        String[] days = {"월", "화", "수", "목", "금"};
        String[] times = {
            "09:00-09:50",
            "10:00-10:50",
            "11:00-11:50",
            "12:00-12:50",
            "13:00-13:50",
            "14:00-14:50",
            "15:00-15:50",
            "16:00-16:50",
            "17:00-17:50"
        };

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(ownerName).append(" 시간표\n\n");

        for (int j = 0; j < 5; j++) {   // 요일 먼저
            for (int i = 0; i < 9; i++) { // 교시 나중
                String val = table[i][j];

                if (val != null && !val.isBlank()) {
                    sb.append("- ")
                            .append(days[j])
                            .append(" ")
                            .append(i + 1)
                            .append("교시 (")
                            .append(times[i])
                            .append("): ")
                            .append(val)
                            .append("\n");
                }
            }
        }

        return sb.toString();
    }

    public String getDaySchedule(String day) {

        int dayIndex = switch (day) {
            case "월" ->
                0;
            case "화" ->
                1;
            case "수" ->
                2;
            case "목" ->
                3;
            case "금" ->
                4;
            default ->
                -1;
        };

        if (dayIndex == -1) {
            return toMarkdown();
        }

        String[] times = {
            "09:00-09:50",
            "10:00-10:50",
            "11:00-11:50",
            "12:00-12:50",
            "13:00-13:50",
            "14:00-14:50",
            "15:00-15:50",
            "16:00-16:50",
            "17:00-17:50"
        };

        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(ownerName)
                .append(" ")
                .append(day)
                .append("요일 시간표\n\n");

        for (int i = 0; i < 9; i++) {
            String val = table[i][dayIndex];

            if (val != null && !val.isBlank()) {
                sb.append("- ")
                        .append(i + 1)
                        .append("교시 (")
                        .append(times[i])
                        .append("): ")
                        .append(val)
                        .append("\n");
            }
        }

        return sb.toString();
    }

    public String getCourseList() {
        java.util.Set<String> courses = new java.util.LinkedHashSet<>();

        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 5; j++) {
                String val = table[i][j];

                if (val != null && !val.isBlank()) {
                    courses.add(val);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(ownerName).append(" 개설 교과목\n\n");

        for (String course : courses) {
            sb.append("- ").append(course).append("\n");
        }

        return sb.toString();
    }
}
