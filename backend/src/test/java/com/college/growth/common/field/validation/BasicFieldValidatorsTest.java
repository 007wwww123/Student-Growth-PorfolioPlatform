package com.college.growth.common.field.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.validator.DerivedFieldValidator;
import com.college.growth.common.field.validation.validator.DormNoValidator;
import com.college.growth.common.field.validation.validator.EmailValidator;
import com.college.growth.common.field.validation.validator.EmergencyTelValidator;
import com.college.growth.common.field.validation.validator.EnumValidator;
import com.college.growth.common.field.validation.validator.FileIdValidator;
import com.college.growth.common.field.validation.validator.IdCardValidator;
import com.college.growth.common.field.validation.validator.MobileValidator;
import com.college.growth.common.field.validation.validator.PersonNameValidator;
import com.college.growth.common.field.validation.validator.StudentNoValidator;
import com.college.growth.common.field.validation.validator.TextLengthValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 字段校验器单测：覆盖板块一的 16 个字段实际用到的规则。
 *
 * <p>校验器只处理非空值，必填判断在业务层，因此这里不测 null 场景。</p>
 */
class BasicFieldValidatorsTest {

    /** 校验位正确的样例，来源：330106 + 20040101 + 001 + 校验位 2。 */
    private static final String VALID_ID_CARD = "330106200401010012";

    @Test
    @DisplayName("姓名：拒绝数字与非法字符，接受少数民族间隔号")
    void personNameRules() {
        PersonNameValidator validator = new PersonNameValidator();
        ArcFieldDef def = def("PERSON_NAME", "{\"minLen\":2,\"maxLen\":32}");

        assertTrue(validator.validate(def, "张伟3").isPresent());
        assertTrue(validator.validate(def, "张").isPresent());
        assertEquals(Optional.empty(), validator.validate(def, "买买提·艾力"));
        assertEquals(Optional.empty(), validator.validate(def, "Li Hua"));
    }

    @Test
    @DisplayName("学号：8–12 位纯数字")
    void studentNoRules() {
        StudentNoValidator validator = new StudentNoValidator();
        ArcFieldDef def = def("STUDENT_NO", "{\"minLen\":8,\"maxLen\":12}");

        assertEquals(Optional.empty(), validator.validate(def, "2023010101"));
        assertTrue(validator.validate(def, "2023010").isPresent());
        assertTrue(validator.validate(def, "20230101a").isPresent());
    }

    @Test
    @DisplayName("身份证：校验位错误、出生日期非法都要拒绝")
    void idCardRules() {
        IdCardValidator validator = new IdCardValidator();
        ArcFieldDef def = def("ID_CARD", null);

        assertEquals(Optional.empty(), validator.validate(def, VALID_ID_CARD));
        assertEquals(Optional.empty(), validator.validate(def, VALID_ID_CARD.toLowerCase()));
        assertTrue(validator.validate(def, "330106200401010013").isPresent());
        assertTrue(validator.validate(def, "330106209901010012").isPresent());
        assertTrue(validator.validate(def, "33010620040101001").isPresent());
    }

    @Test
    @DisplayName("手机号与紧急联系电话：手机 11 位，固话需带区号")
    void phoneRules() {
        MobileValidator mobile = new MobileValidator();
        EmergencyTelValidator tel = new EmergencyTelValidator();
        ArcFieldDef def = def("MOBILE", null);

        assertEquals(Optional.empty(), mobile.validate(def, "13800138000"));
        assertTrue(mobile.validate(def, "12800138000").isPresent());

        assertEquals(Optional.empty(), tel.validate(def, "0571-88881234"));
        assertEquals(Optional.empty(), tel.validate(def, "13800138000"));
        assertTrue(tel.validate(def, "88881234").isPresent());
    }

    @Test
    @DisplayName("枚举：未命中选项即拒绝，选项缺失按配置错误拒绝")
    void enumRules() {
        EnumValidator validator = new EnumValidator();
        ArcFieldDef gender = def("ENUM", null);
        gender.setOptionsJson("[{\"value\":\"M\",\"label\":\"男\"},{\"value\":\"F\",\"label\":\"女\"}]");
        ArcFieldDef noOptions = def("ENUM", null);

        assertEquals(Optional.empty(), validator.validate(gender, "M"));
        assertTrue(validator.validate(gender, "男").isPresent());
        assertTrue(validator.validate(noOptions, "M").isPresent());
    }

    @Test
    @DisplayName("文本长度：按 rule_json 的最小/最大长度判断")
    void textLengthRules() {
        TextLengthValidator validator = new TextLengthValidator();
        ArcFieldDef def = def("TEXT_LEN", "{\"minLen\":2,\"maxLen\":32}");

        assertEquals(Optional.empty(), validator.validate(def, "浙江 杭州"));
        assertTrue(validator.validate(def, "浙").isPresent());
        assertTrue(validator.validate(def, "浙".repeat(33)).isPresent());
    }

    @Test
    @DisplayName("附件：接受单个 ID 或 ID 数组，超过数量上限拒绝")
    void fileRules() {
        FileIdValidator validator = new FileIdValidator();
        ArcFieldDef def = def("FILE_ID", "{\"maxCount\":2}");

        assertEquals(Optional.empty(), validator.validate(def, 10231L));
        assertEquals(Optional.empty(), validator.validate(def, List.of("10231", "10232")));
        assertTrue(validator.validate(def, List.of("10231", "10232", "10233")).isPresent());
        assertTrue(validator.validate(def, "file-10231").isPresent());
    }

    @Test
    @DisplayName("宿舍号与邮箱：格式按 rule_json 正则执行")
    void dormAndEmailRules() {
        DormNoValidator dorm = new DormNoValidator();
        ArcFieldDef dormDef = def("DORM_NO", "{\"regex\":\"^[0-9A-Za-z]{1,4}-[0-9]{3,4}$\"}");
        assertEquals(Optional.empty(), dorm.validate(dormDef, "3-412"));
        assertTrue(dorm.validate(dormDef, "3412").isPresent());

        EmailValidator email = new EmailValidator();
        ArcFieldDef emailDef = def("EMAIL", "{\"maxLen\":128}");
        assertEquals(Optional.empty(), email.validate(emailDef, "zhangwei@example.edu.cn"));
        assertTrue(email.validate(emailDef, "zhangwei@example").isPresent());
    }

    @Test
    @DisplayName("系统推导字段：任何值都不接受")
    void derivedAlwaysRejected() {
        DerivedFieldValidator validator = new DerivedFieldValidator();
        assertTrue(validator.validate(def("DERIVED", null), "软件工程").isPresent());
    }

    @Test
    @DisplayName("注册表：未注册的校验器标识按配置错误抛出，不能静默放行")
    void unknownValidatorFailsFast() {
        FieldValidatorRegistry registry = new FieldValidatorRegistry(List.of(new MobileValidator()));
        ArcFieldDef def = def("NOT_REGISTERED", null);
        IllegalStateException ex = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> registry.validate(def, "13800138000"));
        assertTrue(ex.getMessage().contains("NOT_REGISTERED"));
    }

    private ArcFieldDef def(String validator, String ruleJson) {
        ArcFieldDef def = new ArcFieldDef();
        def.setFieldCode("basic.test");
        def.setFieldType("TEXT");
        def.setValidator(validator);
        def.setRuleJson(ruleJson);
        return def;
    }
}
