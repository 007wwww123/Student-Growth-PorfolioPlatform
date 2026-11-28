package com.college.growth.export.service;

import java.util.List;
import lombok.Data;

/** PDF 渲染所需的数据快照（与持久层解耦，便于单测）。 */
@Data
public class ProfileExportData {

    private String studentName;

    private String studentNo;

    private String className;

    private String majorName;

    private String academicYear;

    private String exporterName;

    private String exportedAt;

    private List<Section> sections;

    private List<String> certificates;

    private List<String> events;

    @Data
    public static class Section {

        private String name;

        private String status;

        private String completeness;

        private List<Line> lines;
    }

    @Data
    public static class Line {

        private String label;

        private String value;

        public Line(String label, String value) {
            this.label = label;
            this.value = value;
        }
    }
}
