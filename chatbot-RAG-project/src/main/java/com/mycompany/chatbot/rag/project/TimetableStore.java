package com.mycompany.chatbot.rag.project;

import java.util.HashMap;
import java.util.Map;

public class TimetableStore {

    private final Map<String, Timetable> professorSchedules = new HashMap<>();
    private final Map<String, Timetable> classroomSchedules = new HashMap<>();

    public void addProfessorSchedule(String professor, Timetable timetable) {
        professorSchedules.put(professor, timetable);
    }

    public void addClassroomSchedule(String classroom, Timetable timetable) {
        classroomSchedules.put(classroom, timetable);
    }

    public Timetable getProfessorSchedule(String professor) {
        return professorSchedules.get(professor);
    }

    public Timetable getClassroomSchedule(String classroom) {
        return classroomSchedules.get(classroom);
    }

    public boolean hasProfessor(String professor) {
        return professorSchedules.containsKey(professor);
    }

    public boolean hasClassroom(String classroom) {
        return classroomSchedules.containsKey(classroom);
    }
}
