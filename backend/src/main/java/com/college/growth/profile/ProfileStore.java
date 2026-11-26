package com.college.growth.profile;

import com.college.growth.common.enums.ProfileStatusEnum;
import com.college.growth.common.enums.SectionCodeEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.result.FieldErrorDetail;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.web.RequestVersion;
import com.college.growth.identity.ArchiveIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileStore {
    private final ProfileRepository profiles;
    private final ObjectMapper json;

    ProfileStore(ProfileRepository profiles, ObjectMapper json) {
        this.profiles = profiles;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public <T> T detail(SectionCodeEnum code, long student, String year, Class<T> type) {
        var identity = ArchiveIdentity.current();
        identity.requireRead(student);
        if (identity.role() == ArchiveIdentity.Role.MENTOR && code != SectionCodeEnum.MENTOR)
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED);
        if (year == null) year = identity.academicYears().stream().max(String::compareTo)
                .orElseThrow(() -> new BusinessException(ResultCode.DATA_SCOPE_DENIED));
        identity.requireYear(year);
        var row = profiles.find(student,year,code,false);
        var stored = profiles.values(row.id());
        var values = new LinkedHashMap<String,Object>();
        var definitions = new ArrayList<Map<String,Object>>();
        boolean editable = identity.canWrite(student,code.getCode()) && ProfileStatusEnum.valueOf(row.status()).isWritable();
        for (var field : profiles.definitions(code)) {
            if (!visible(field,identity)) continue;
            boolean writable = editable && writable(field,identity,stored);
            Map<String,Object> definition = new LinkedHashMap<>(Map.of("key",field.key(),"label",field.label(),
                    "type",field.type(),"required",field.required(),"editable",writable));
            List<String> options = ProfileValidators.options(field.validator());
            if (!options.isEmpty()) definition.put("options",options);
            definitions.add(definition);
            Object value = stored.get(field.key());
            if (field.key().equals("phone") && value instanceof String phone && phone.length()==11
                    && identity.role()!=ArchiveIdentity.Role.STUDENT && identity.role()!=ArchiveIdentity.Role.ADMIN) {
                value = phone.substring(0,3)+"****"+phone.substring(7);
            }
            if (value != null) values.put(field.key(),value);
        }
        Map<String,Object> result = new LinkedHashMap<>(Map.of("studentId",student,"academicYear",year,"sectionCode",code.name(),
                "sectionName",code.getLabel(),"status",row.status(),"version",row.version(),"editable",editable,"fields",values,"fieldDefinitions",definitions));
        result.put("returnReason",row.returnReason());
        return json.convertValue(result,type);
    }

    @Transactional
    public void save(SectionCodeEnum code, long student, String year, Map<String,Object> patch) {
        var row = writableRow(code,student,year);
        var identity = ArchiveIdentity.current();
        var values = profiles.values(row.id());
        Map<String,ProfileRepository.Field> definitions = new HashMap<>();
        for (var f : profiles.definitions(code)) definitions.put(f.key(),f);
        for (var entry : patch.entrySet()) {
            var field = definitions.get(entry.getKey());
            if (field == null) throw invalid(entry.getKey(),"未知字段");
            if (!visible(field,identity) || !writable(field,identity,values)) throw new BusinessException(ResultCode.FIELD_NOT_EDITABLE);
            Object value = entry.getValue();
            if (!valid(field,value)) throw invalid(field.key(),"字段格式错误");
            profiles.write(row.id(),field.key(),value);
        }
        profiles.record(row,identity.actorId(),false);
    }

    @Transactional
    public void submit(SectionCodeEnum code, long student, String year) {
        var row = writableRow(code,student,year);
        var role = ArchiveIdentity.current().role();
        if (role != ArchiveIdentity.Role.STUDENT && role != ArchiveIdentity.Role.MENTOR)
            throw new BusinessException(ResultCode.DATA_SCOPE_DENIED);
        var fields = profiles.values(row.id());
        var missing = new ArrayList<FieldErrorDetail>();
        for (var field : profiles.definitions(code)) {
            Object value = fields.get(field.key());
            if (value != null && !valid(field,value)) throw invalid(field.key(),"字段值不符合当前规则");
            if (field.required() && (value==null || value instanceof String s && s.isBlank() || value instanceof List<?> list && list.isEmpty())) {
                missing.add(new FieldErrorDetail(field.key(),"REQUIRED",field.label()+"不能为空"));
            }
        }
        if (!missing.isEmpty()) throw new BusinessException(ResultCode.REQUIRED_FIELD_MISSING,"必填内容未填写",missing);
        if (fields.get("hobbies") instanceof List<?> hobbies && hobbies.contains("OTHER") && Objects.toString(fields.get("hobbyDesc"),"").isBlank())
            throw invalid("hobbyDesc","选择其他兴趣时请补充说明");
        if (fields.get("strongSubjects") instanceof List<?> strong && fields.get("weakSubjects") instanceof List<?> weak && !Collections.disjoint(strong,weak))
            throw invalid("weakSubjects","优势与待提升科目不能重复");
        profiles.record(row,ArchiveIdentity.current().actorId(),true);
    }

    private ProfileRepository.Row writableRow(SectionCodeEnum code,long student,String year) {
        var identity = ArchiveIdentity.current();
        identity.requireYear(year);
        identity.requireWrite(student,code.getCode());
        long expected = RequestVersion.required();
        var row = profiles.find(student,year,code,true);
        if (row.version()!=expected || !ProfileStatusEnum.valueOf(row.status()).isWritable())
            throw new BusinessException(ResultCode.STATE_CONFLICT);
        return row;
    }

    private static boolean visible(ProfileRepository.Field field,ArchiveIdentity identity) {
        return switch(field.access()) {
            case "HIDDEN" -> false;
            case "SELF" -> identity.role()==ArchiveIdentity.Role.STUDENT;
            case "SECTION","SCHOOL","IMMUTABLE","COUNSELOR","READ_ONLY" -> true;
            default -> false;
        };
    }

    private static boolean writable(ProfileRepository.Field field,ArchiveIdentity identity,Map<String,Object> stored) {
        return switch(field.access()) {
            case "SCHOOL" -> identity.role()==ArchiveIdentity.Role.ADMIN;
            case "COUNSELOR" -> identity.role()==ArchiveIdentity.Role.COUNSELOR;
            case "IMMUTABLE" -> identity.role()==ArchiveIdentity.Role.ADMIN && !stored.containsKey(field.key());
            case "SELF" -> identity.role()==ArchiveIdentity.Role.STUDENT;
            case "SECTION" -> identity.role()==ArchiveIdentity.Role.STUDENT || identity.role()==ArchiveIdentity.Role.MENTOR;
            default -> false;
        };
    }

    private static boolean valid(ProfileRepository.Field f,Object value) {
        if (value == null) return true;
        boolean typeMatches = switch(f.type()) {
            case "tags" -> value instanceof List<?> list && list.size()<=100 && list.stream().allMatch(v -> v instanceof String s && s.length()<=255 && !s.isBlank());
            case "number" -> value instanceof Integer || value instanceof Long;
            case "date" -> {
                try { LocalDate.parse(String.valueOf(value)); yield true; }
                catch(DateTimeParseException ex) { yield false; }
            }
            case "text","textarea" -> value instanceof String s && s.length()<=4000;
            default -> false;
        };
        return typeMatches && (f.validator()==null || ProfileValidators.accepts(f.validator(),value));
    }

    private static BusinessException invalid(String key,String message) {
        return new BusinessException(ResultCode.FIELD_VALIDATION_FAILED,message,List.of(new FieldErrorDetail(key,"FORMAT",message)));
    }
}
