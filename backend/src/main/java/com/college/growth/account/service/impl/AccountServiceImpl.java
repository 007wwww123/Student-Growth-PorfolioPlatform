package com.college.growth.account.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.college.growth.account.dto.BatchOpenDTO;
import com.college.growth.account.dto.UserCreateDTO;
import com.college.growth.account.service.AccountService;
import com.college.growth.account.vo.AccountUserVO;
import com.college.growth.account.vo.BatchOpenItemVO;
import com.college.growth.account.vo.BatchOpenResultVO;
import com.college.growth.account.vo.CreateAccountResultVO;
import com.college.growth.account.vo.ResetPasswordResultVO;
import com.college.growth.auth.entity.SysUser;
import com.college.growth.auth.mapper.SysUserMapper;
import com.college.growth.auth.service.PasswordHasher;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.entity.OrgClass;
import com.college.growth.common.org.mapper.OrgClassMapper;
import com.college.growth.common.result.PageResult;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.security.IdCardCrypto;
import com.college.growth.common.student.entity.StuStudent;
import com.college.growth.common.student.mapper.StuStudentMapper;
import com.college.growth.common.util.TextUtils;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号管理实现（F-03）。
 *
 * <p>账号口径沿用登录体系：学生账号 {@code id = student_id}、登录名 = 学号；
 * 辅导员/管理员使用雪花 ID 与自填登录名。初始密码按 DC-04 默认「身份证后六位」，
 * 没有身份证时退化为学号后六位；管理员也可显式传入密码。</p>
 *
 * <p>安全边界：初始密码只在创建/重置响应中返回一次，审计与日志只记动作不记密码；
 * 不允许停用自己、不允许把学生账号改成其他角色。</p>
 */
@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountServiceImpl.class);

    private static final int ENABLED = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final char[] UPPERS = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private static final char[] LOWERS = "abcdefghijkmnpqrstuvwxyz".toCharArray();

    private static final char[] DIGITS = "23456789".toCharArray();

    private static final char[] SYMBOLS = "!@#$%^&*".toCharArray();

    private final SysUserMapper userMapper;

    private final StuStudentMapper studentMapper;

    private final OrgClassMapper classMapper;

    private final PasswordHasher passwordHasher;

    private final AuditLogService auditLogService;

    public AccountServiceImpl(SysUserMapper userMapper, StuStudentMapper studentMapper,
                              OrgClassMapper classMapper, PasswordHasher passwordHasher,
                              AuditLogService auditLogService) {
        this.userMapper = userMapper;
        this.studentMapper = studentMapper;
        this.classMapper = classMapper;
        this.passwordHasher = passwordHasher;
        this.auditLogService = auditLogService;
    }

    @Override
    public PageResult<AccountUserVO> list(String role, Integer status, String keyword,
                                          long pageNum, long pageSize) {
        requireAdmin();
        long num = Math.max(1, pageNum);
        long size = Math.min(Math.max(1, pageSize), 100);
        UserRoleEnum roleEnum = TextUtils.isBlank(role) ? null : parseRole(role);
        String kw = TextUtils.isBlank(keyword) ? null : keyword.trim();
        IPage<SysUser> page = userMapper.selectPage(new Page<>(num, size),
                Wrappers.<SysUser>lambdaQuery()
                        .eq(roleEnum != null, SysUser::getRole, roleEnum == null ? null : roleEnum.name())
                        .eq(status != null, SysUser::getStatus, status)
                        .and(kw != null, wrapper -> wrapper.like(SysUser::getUsername, kw)
                                .or().like(SysUser::getDisplayName, kw))
                        .orderByAsc(SysUser::getId));
        List<SysUser> records = page.getRecords();
        Map<Long, StuStudent> students = loadStudents(records);
        Map<String, String> classNames = classNames(students.values());
        List<AccountUserVO> rows = new ArrayList<>(records.size());
        for (SysUser user : records) {
            StuStudent student = user.getStudentId() == null ? null : students.get(user.getStudentId());
            rows.add(toVO(user, student, classNames));
        }
        return new PageResult<>(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateAccountResultVO create(UserCreateDTO dto) {
        requireAdmin();
        UserRoleEnum role = parseRole(dto.getRole());
        SysUser user = new SysUser();
        String initialPassword;
        if (role == UserRoleEnum.STU) {
            StuStudent student = requireStudent(dto.getStudentId());
            if (userMapper.selectById(student.getId()) != null) {
                throw new BusinessException(ResultCode.STATE_CONFLICT,
                        "该学生已有账号：" + student.getStudentNo());
            }
            user.setId(student.getId());
            user.setUsername(student.getStudentNo());
            user.setDisplayName(student.getName());
            user.setStudentId(student.getId());
            initialPassword = TextUtils.isBlank(dto.getPassword())
                    ? initialPasswordOf(student) : requireStrongPassword(dto.getPassword());
        } else {
            String username = TextUtils.isBlank(dto.getUsername()) ? null : dto.getUsername().trim();
            if (username == null) {
                throw new BusinessException(ResultCode.PARAM_MISSING, "登录名不能为空");
            }
            user.setId(IdWorker.getId());
            user.setUsername(username);
            user.setDisplayName(dto.getDisplayName().trim());
            initialPassword = TextUtils.isBlank(dto.getPassword())
                    ? randomPassword() : requireStrongPassword(dto.getPassword());
        }
        if (findByUsername(user.getUsername(), null) != null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "登录名已存在：" + user.getUsername());
        }
        user.setRole(role.name());
        user.setPasswordHash(passwordHasher.hash(initialPassword));
        user.setStatus(ENABLED);
        user.setTempGrant(0);
        userMapper.insert(user);
        audit(AuditLogService.ACTION_CREATE, user.getId(), "开通账号：" + user.getUsername()
                + "（" + role.getLabel() + "）");

        CreateAccountResultVO result = new CreateAccountResultVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setDisplayName(user.getDisplayName());
        result.setRole(user.getRole());
        result.setRoleLabel(role.getLabel());
        result.setInitialPassword(initialPassword);
        log.info("开通账号 id={} role={} operator={}", user.getId(), role, UserContext.get().userId());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResetPasswordResultVO resetPassword(Long userId, String newPassword) {
        requireAdmin();
        SysUser user = requireUser(userId);
        String password = TextUtils.isBlank(newPassword) ? randomPassword()
                : requireStrongPassword(newPassword);
        user.setPasswordHash(passwordHasher.hash(password));
        userMapper.updateById(user);
        audit(AuditLogService.ACTION_RESET, user.getId(), "重置密码：" + user.getUsername());

        ResetPasswordResultVO result = new ResetPasswordResultVO();
        result.setUserId(user.getId());
        result.setUsername(user.getUsername());
        result.setNewPassword(password);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, int status) {
        requireAdmin();
        SysUser user = requireUser(userId);
        if (status == 0 && Objects.equals(user.getId(), UserContext.get().userId())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "不能停用当前登录账号");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        audit(status == ENABLED ? AuditLogService.ACTION_ENABLE : AuditLogService.ACTION_DISABLE,
                user.getId(), (status == ENABLED ? "启用" : "停用") + "账号：" + user.getUsername());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRole(Long userId, String role) {
        requireAdmin();
        SysUser user = requireUser(userId);
        UserRoleEnum target = parseRole(role);
        if (user.getStudentId() != null) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "学生账号角色由学籍决定，不能修改");
        }
        if (Objects.equals(user.getId(), UserContext.get().userId())) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "不能修改当前登录账号的角色");
        }
        user.setRole(target.name());
        userMapper.updateById(user);
        audit(AuditLogService.ACTION_ROLE_ASSIGN, user.getId(),
                "角色调整为：" + target.getLabel() + "（" + user.getUsername() + "）");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchOpenResultVO batchOpen(BatchOpenDTO dto) {
        requireAdmin();
        List<String> classCodes = normalizeClasses(dto.getClassCodes());
        List<StuStudent> students = studentMapper.selectList(Wrappers.<StuStudent>lambdaQuery()
                .in(StuStudent::getClassCode, classCodes)
                .eq(StuStudent::getStatus, 1)
                .orderByAsc(StuStudent::getStudentNo));
        if (students.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "所选班级下没有在读学生");
        }
        Set<String> existingUsernames = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .in(SysUser::getUsername, students.stream().map(StuStudent::getStudentNo).toList()))
                .stream().map(SysUser::getUsername).collect(Collectors.toSet());
        Set<Long> existingIds = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                        .in(SysUser::getId, students.stream().map(StuStudent::getId).toList()))
                .stream().map(SysUser::getId).collect(Collectors.toSet());

        int created = 0;
        List<BatchOpenItemVO> accounts = new ArrayList<>();
        for (StuStudent student : students) {
            if (existingIds.contains(student.getId()) || existingUsernames.contains(student.getStudentNo())) {
                continue;
            }
            String initialPassword = initialPasswordOf(student);
            SysUser user = new SysUser();
            user.setId(student.getId());
            user.setUsername(student.getStudentNo());
            user.setDisplayName(student.getName());
            user.setRole(UserRoleEnum.STU.name());
            user.setStudentId(student.getId());
            user.setPasswordHash(passwordHasher.hash(initialPassword));
            user.setStatus(ENABLED);
            user.setTempGrant(0);
            userMapper.insert(user);
            created++;

            BatchOpenItemVO item = new BatchOpenItemVO();
            item.setStudentId(student.getId());
            item.setStudentNo(student.getStudentNo());
            item.setStudentName(student.getName());
            item.setUsername(student.getStudentNo());
            item.setInitialPassword(initialPassword);
            accounts.add(item);
        }
        audit(AuditLogService.ACTION_BATCH_OPEN, null,
                "批量开通账号：班级 " + classCodes + "，新建 " + created + " 个");
        log.info("批量开通账号 classes={} students={} created={}", classCodes, students.size(), created);

        BatchOpenResultVO result = new BatchOpenResultVO();
        result.setTotalStudents(students.size());
        result.setCreated(created);
        result.setSkipped(students.size() - created);
        result.setAccounts(accounts);
        return result;
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private Map<Long, StuStudent> loadStudents(List<SysUser> users) {
        List<Long> ids = users.stream().map(SysUser::getStudentId)
                .filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return studentMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StuStudent::getId, item -> item));
    }

    private Map<String, String> classNames(Iterable<StuStudent> students) {
        Set<String> codes = new LinkedHashSet<>();
        for (StuStudent student : students) {
            if (!TextUtils.isBlank(student.getClassCode())) {
                codes.add(student.getClassCode());
            }
        }
        if (codes.isEmpty()) {
            return Map.of();
        }
        return classMapper.selectList(Wrappers.<OrgClass>lambdaQuery()
                        .in(OrgClass::getClassCode, codes))
                .stream().collect(Collectors.toMap(OrgClass::getClassCode, OrgClass::getClassName));
    }

    private AccountUserVO toVO(SysUser user, StuStudent student, Map<String, String> classNames) {
        AccountUserVO vo = new AccountUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setDisplayName(user.getDisplayName());
        vo.setRole(user.getRole());
        vo.setRoleLabel(UserRoleEnum.fromCode(user.getRole()).getLabel());
        vo.setStudentId(user.getStudentId());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        if (student != null) {
            vo.setStudentNo(student.getStudentNo());
            vo.setClassCode(student.getClassCode());
            vo.setClassName(classNames.get(student.getClassCode()));
        }
        return vo;
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userId == null ? null : userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "账号不存在：" + userId);
        }
        return user;
    }

    private StuStudent requireStudent(Long studentId) {
        StuStudent student = studentId == null ? null : studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "学生不存在：" + studentId);
        }
        if (student.getStatus() == null || student.getStatus() != 1) {
            throw new BusinessException(ResultCode.STATE_CONFLICT, "学生不是在读状态：" + student.getStudentNo());
        }
        return student;
    }

    private SysUser findByUsername(String username, Long excludeId) {
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId)
                .last("limit 1"));
    }

    private UserRoleEnum parseRole(String role) {
        if (TextUtils.isBlank(role)) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "角色不能为空");
        }
        try {
            return UserRoleEnum.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "未知角色：" + role);
        }
    }

    private List<String> normalizeClasses(List<String> raw) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String code : raw) {
            if (!TextUtils.isBlank(code)) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "请至少选择一个班级");
        }
        if (codes.size() > 20) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "单次最多选择 20 个班级");
        }
        return List.copyOf(codes);
    }

    /** 初始密码：身份证后六位（DC-04）；无身份证时退化为学号后六位。 */
    private String initialPasswordOf(StuStudent student) {
        String idCard = IdCardCrypto.plain(student.getIdCard(), student.getIdCardCipher());
        if (!TextUtils.isBlank(idCard) && idCard.length() >= 6) {
            return idCard.substring(idCard.length() - 6);
        }
        String no = student.getStudentNo();
        return no != null && no.length() >= 6 ? no.substring(no.length() - 6) : randomPassword();
    }

    /** 显式密码必须 8-20 位且至少包含大写、小写、数字、符号中的三类（BR-A02）。 */
    private String requireStrongPassword(String password) {
        String value = password == null ? "" : password;
        if (value.length() < 8 || value.length() > 20) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "密码长度应为 8-20 位");
        }
        int kinds = 0;
        if (value.chars().anyMatch(Character::isUpperCase)) {
            kinds++;
        }
        if (value.chars().anyMatch(Character::isLowerCase)) {
            kinds++;
        }
        if (value.chars().anyMatch(Character::isDigit)) {
            kinds++;
        }
        if (value.chars().anyMatch(ch -> !Character.isLetterOrDigit(ch))) {
            kinds++;
        }
        if (kinds < 3) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "密码须包含大写字母、小写字母、数字、符号中的至少三类");
        }
        return value;
    }

    /** 生成满足 8-20 位与三类字符要求的随机密码。 */
    private String randomPassword() {
        List<Character> chars = new ArrayList<>();
        chars.add(UPPERS[RANDOM.nextInt(UPPERS.length)]);
        chars.add(LOWERS[RANDOM.nextInt(LOWERS.length)]);
        chars.add(DIGITS[RANDOM.nextInt(DIGITS.length)]);
        char[] all = (new String(UPPERS) + new String(LOWERS) + new String(DIGITS) + new String(SYMBOLS))
                .toCharArray();
        for (int i = 0; i < 5; i++) {
            chars.add(all[RANDOM.nextInt(all.length)]);
        }
        for (int i = chars.size() - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            Character tmp = chars.get(i);
            chars.set(i, chars.get(j));
            chars.set(j, tmp);
        }
        StringBuilder builder = new StringBuilder(chars.size());
        chars.forEach(builder::append);
        return builder.toString();
    }

    private void requireAdmin() {
        CurrentUser user = UserContext.get();
        if (user.role() != UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "仅管理员可以管理账号");
        }
    }

    private void audit(String action, Long id, String detail) {
        auditLogService.record(AuditLogService.BIZ_ACCOUNT, AuditLogService.TARGET_USER, id,
                null, null, null, action, detail);
    }
}
