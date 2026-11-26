package com.college.growth.profile;

import com.college.growth.common.enums.SectionCodeEnum;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ProfileRepository {
    record Row(long id, String status, long version, String returnReason) {}
    record Field(String key, String label, String type, boolean required, String access, String validator) {}

    private final JdbcTemplate db;
    private final ObjectMapper json;

    ProfileRepository(JdbcTemplate db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    Row find(long student, String year, SectionCodeEnum section, boolean lock) {
        if (lock) {
            db.update("INSERT INTO arc_profile(student_id,academic_year,section_code) VALUES(?,?,?) ON DUPLICATE KEY UPDATE id=arc_profile.id",
                    student, year, section.name());
        }
        String sql = "SELECT id,status,version,return_reason FROM arc_profile WHERE student_id=? AND academic_year=? AND section_code=?";
        List<Row> rows = db.query(sql + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)), student, year, section.name());
        return rows.isEmpty() ? new Row(0,"DRAFT",0,null) : rows.get(0);
    }

    List<Field> definitions(SectionCodeEnum code) {
        return db.query("SELECT field_key,label,field_type,required_flag,access_rule,validator FROM arc_field_def WHERE section_code=? ORDER BY sort_order,field_key",
                (rs,n) -> new Field(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getString(5),rs.getString(6)), code.name());
    }

    Map<String,Object> values(long id) {
        Map<String,Object> values = new LinkedHashMap<>();
        db.query("SELECT field_key,field_value FROM arc_profile_item WHERE profile_id=?", rs -> {
            try {
                values.put(rs.getString(1), json.readValue(rs.getString(2), Object.class));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("字段值格式错误", ex);
            }
        }, id);
        return values;
    }

    void write(long id, String key, Object value) {
        if (value == null) {
            db.update("DELETE FROM arc_profile_item WHERE profile_id=? AND field_key=?", id,key);
            return;
        }
        try {
            db.update("INSERT INTO arc_profile_item(profile_id,field_key,field_value) VALUES(?,?,?) ON DUPLICATE KEY UPDATE field_value=VALUES(field_value)",
                    id,key,json.writeValueAsString(value));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("字段值格式错误",ex);
        }
    }

    void record(Row row, long actor, boolean submit) {
        String status = submit ? "SUBMITTED" : row.status();
        db.update("UPDATE arc_profile SET version=version+1,status=?,submit_count=submit_count+? WHERE id=?",status,submit ? 1 : 0,row.id());
        db.update("INSERT INTO arc_profile_log(profile_id,actor_id,from_status,to_status,version) VALUES(?,?,?,?,?)",
                row.id(),actor,row.status(),status,row.version()+1);
    }
}
