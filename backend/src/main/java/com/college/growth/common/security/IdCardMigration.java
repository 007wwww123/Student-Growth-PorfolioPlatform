package com.college.growth.common.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.college.growth.common.profile.entity.ArcProfileItem;
import com.college.growth.common.profile.mapper.ArcProfileItemMapper;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 身份证存量迁移（脚本 22 之后执行）。
 *
 * <p>把 {@code stu_student.id_card} 的明文加密进 {@code id_card_cipher}、写
 * {@code id_card_hash}，然后清空明文列；把 {@code arc_profile_item} 中
 * {@code basic.idCard} 的明文值替换为密文。幂等：只处理未加密行。</p>
 */
@Component
public class IdCardMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdCardMigration.class);

    private static final String ID_CARD_FIELD = "basic.idCard";

    private final StuStudentMapper studentMapper;

    private final ArcProfileItemMapper itemMapper;

    public IdCardMigration(StuStudentMapper studentMapper, ArcProfileItemMapper itemMapper) {
        this.studentMapper = studentMapper;
        this.itemMapper = itemMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrateStudents();
            migrateItems();
        } catch (Exception ex) {
            // 迁移失败不应让应用启动失败：登录/只读仍可用；但要留下明确错误
            log.error("身份证加密迁移失败，请检查 ID_CARD_KEY 与数据库", ex);
        }
    }

    private void migrateStudents() {
        var students = studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                .and(wrapper -> wrapper.isNotNull(StuStudent::getIdCard).ne(StuStudent::getIdCard, "")));
        int migrated = 0;
        for (StuStudent student : students) {
            String plain = student.getIdCard();
            if (TextUtils.isBlank(plain)) {
                continue;
            }
            String cipher = TextUtils.isBlank(student.getIdCardCipher())
                    ? IdCardCrypto.encrypt(plain) : student.getIdCardCipher();
            String hash = TextUtils.isBlank(student.getIdCardHash())
                    ? IdCardCrypto.hash(plain) : student.getIdCardHash();
            studentMapper.update(null, Wrappers.<StuStudent>lambdaUpdate()
                    .eq(StuStudent::getId, student.getId())
                    .set(StuStudent::getIdCardCipher, cipher)
                    .set(StuStudent::getIdCardHash, hash)
                    .set(StuStudent::getIdCard, null));
            migrated++;
        }
        if (migrated > 0) {
            log.info("身份证明文迁移完成 students={}", migrated);
        }
    }

    private void migrateItems() {
        var items = itemMapper.selectList(Wrappers.<ArcProfileItem>lambdaQuery()
                .eq(ArcProfileItem::getFieldCode, ID_CARD_FIELD)
                .isNotNull(ArcProfileItem::getValueText));
        int migrated = 0;
        for (ArcProfileItem item : items) {
            String value = item.getValueText();
            if (TextUtils.isBlank(value) || IdCardCrypto.isEncrypted(value)) {
                continue;
            }
            itemMapper.update(null, Wrappers.<ArcProfileItem>lambdaUpdate()
                    .eq(ArcProfileItem::getId, item.getId())
                    .set(ArcProfileItem::getValueText, IdCardCrypto.encrypt(value)));
            migrated++;
        }
        if (migrated > 0) {
            log.info("档案字段身份证密文迁移完成 items={}", migrated);
        }
    }
}
