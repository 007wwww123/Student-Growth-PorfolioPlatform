package com.college.growth.common.field.validation.validator;

import com.college.growth.common.field.FieldValues;
import com.college.growth.common.field.entity.ArcFieldDef;
import com.college.growth.common.field.validation.FieldValidator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 身份证号校验（ID_CARD）：18 位、末位校验码、出生日期合法且不晚于今天。
 *
 * <p>校验位算法必须实现，否则「随手编 18 位」也能入库，导出后无法与学籍核对。</p>
 */
@Component
public class IdCardValidator implements FieldValidator {

    private static final Pattern FORMAT = Pattern.compile("^\\d{17}[\\dXx]$");

    private static final int[] WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    private static final char[] CHECK_CODES = "10X98765432".toCharArray();

    private static final DateTimeFormatter BIRTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public String code() {
        return "ID_CARD";
    }

    @Override
    public Optional<String> validate(ArcFieldDef def, Object value) {
        String text = FieldValues.asText(value);
        if (text == null) {
            return Optional.of("身份证号只接受文本");
        }
        String idCard = text.trim().toUpperCase();
        if (!FORMAT.matcher(idCard).matches()) {
            return Optional.of("身份证号应为 18 位，末位可为 X");
        }
        if (CHECK_CODES[checksum(idCard)] != idCard.charAt(17)) {
            return Optional.of("身份证号校验位不正确");
        }
        try {
            LocalDate birth = LocalDate.parse(idCard.substring(6, 14), BIRTH_FORMAT);
            if (birth.isAfter(LocalDate.now())) {
                return Optional.of("身份证号中的出生日期晚于今天");
            }
        } catch (DateTimeParseException ex) {
            return Optional.of("身份证号中的出生日期不合法");
        }
        return Optional.empty();
    }

    private int checksum(String idCard) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * WEIGHTS[i];
        }
        return sum % 11;
    }
}
