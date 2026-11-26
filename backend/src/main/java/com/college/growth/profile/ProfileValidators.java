package com.college.growth.profile;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

final class ProfileValidators {
    private static final Map<String,List<String>> OPTIONS = Map.of(
            "GENDER", List.of("M","F","U"),
            "ORG_LEVEL", List.of("HIGH","MID","LOW"),
            "SLEEP", List.of("EARLY","NORMAL","LATE"),
            "EXERCISE", List.of("NONE","LOW","MID","HIGH"),
            "MOOD", List.of("GOOD","FAIR","LOW"),
            "INTENTION", List.of("EMPLOY","POSTGRAD","OVERSEAS","STARTUP","UNDECIDED")
    );

    private ProfileValidators() {}

    static List<String> options(String rule) {
        return rule == null ? List.of() : OPTIONS.getOrDefault(rule,List.of());
    }

    static boolean accepts(String rule, Object value) {
        if (value instanceof String s) {
            List<String> allowed = options(rule);
            if (!allowed.isEmpty()) return allowed.contains(s);
            return switch (rule) {
                case "NAME" -> s.length() >= 2 && s.length() <= 32 && !s.isBlank() && s.chars().noneMatch(Character::isDigit);
                case "STUDENT_NO" -> s.matches("[0-9]{8,12}");
                case "PHONE" -> s.matches("1[3-9][0-9]{9}");
                case "EMAIL" -> s.length() <= 128 && s.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
                case "GOAL_SHORT" -> s.strip().length() >= 20 && s.length() <= 200;
                case "GOAL_LONG" -> s.strip().length() >= 20 && s.length() <= 300;
                case "ADVICE" -> s.strip().length() >= 20 && s.length() <= 1000;
                case "TEXT_64" -> s.length() <= 64;
                case "TEXT_300" -> s.length() <= 300;
                case "TEXT_500" -> s.length() <= 500;
                case "TALK_DATE" -> !LocalDate.parse(s).isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")));
                default -> false;
            };
        }
        if (value instanceof List<?> list) {
            return switch (rule) {
                case "SUBJECTS" -> list.size() <= 3;
                case "HOBBIES" -> list.size() <= 5;
                default -> false;
            };
        }
        if (value instanceof Number n) {
            long i = n.longValue();
            return switch (rule) {
                case "STRESS" -> i >= 1 && i <= 5;
                case "STAR" -> i >= 0 && i <= 2;
                default -> false;
            };
        }
        return false;
    }
}
