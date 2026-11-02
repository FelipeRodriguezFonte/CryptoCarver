package com.cryptocarver.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Who is working on which exercise while their operations are recorded for hand-in. */
public record ExerciseSession(String student, String exercise, String startedAt) {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public ExerciseSession {
        if (student == null || student.isBlank()) throw new IllegalArgumentException("Student is required");
        if (exercise == null || exercise.isBlank()) throw new IllegalArgumentException("Exercise is required");
        student = student.trim();
        exercise = exercise.trim();
    }

    public static ExerciseSession start(String student, String exercise) {
        return new ExerciseSession(student, exercise, LocalDateTime.now().format(TIMESTAMP_FORMAT));
    }

    /** File name that sorts a folder of hand-ins by exercise and then by student. */
    public String suggestedFileName() {
        return (exercise + "-" + student).replaceAll("[^\\p{L}\\p{N}._-]+", "_") + EvidencePackageCodec.EXTENSION;
    }
}
