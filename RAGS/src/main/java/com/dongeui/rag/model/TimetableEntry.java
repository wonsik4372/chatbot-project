package com.dongeui.rag.model;

public class TimetableEntry {

    private String semester;

    private String weekday;

    private String period;

    private String subject;

    private String professor;

    private String room;

    private String type;

    public TimetableEntry() {
    }

    public TimetableEntry(
            String semester,
            String weekday,
            String period,
            String subject,
            String professor,
            String room,
            String type
    ) {
        this.semester = semester;
        this.weekday = weekday;
        this.period = period;
        this.subject = subject;
        this.professor = professor;
        this.room = room;
        this.type = type;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getWeekday() {
        return weekday;
    }

    public void setWeekday(String weekday) {
        this.weekday = weekday;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getProfessor() {
        return professor;
    }

    public void setProfessor(String professor) {
        this.professor = professor;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}