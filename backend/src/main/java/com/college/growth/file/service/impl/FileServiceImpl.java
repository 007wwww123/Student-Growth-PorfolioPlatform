package com.college.growth.file.service.impl;

import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.common.util.AcademicYearUtils;
import com.college.growth.common.util.TextUtils;
import com.college.growth.file.entity.SysFile;
import com.college.growth.file.mapper.SysFileMapper;
import com.college.growth.file.service.FileService;
import com.college.growth.file.vo.FileVO;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 本地附件存储（B-6）。
 *
 * <p>只存随机文件名，原始文件名仅入库用于展示；限制 10MB 与图片/PDF 类型；
 * 下载必须经过鉴权接口并写审计。生产可替换为 MinIO，元数据表不变。</p>
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    private final SysFileMapper mapper;

    private final StudentClassService studentClassService;

    private final AuditLogService auditLogService;

    private final Path dir;

    public FileServiceImpl(SysFileMapper mapper, StudentClassService studentClassService,
                           AuditLogService auditLogService,
                           @Value("${growth.file.dir:}") String dir) {
        this.mapper = mapper;
        this.studentClassService = studentClassService;
        this.auditLogService = auditLogService;
        String configured = TextUtils.isBlank(dir)
                ? System.getProperty("java.io.tmpdir") + File.separator + "student-growth-files"
                : dir;
        this.dir = Paths.get(configured);
    }

    @Override
    public FileVO upload(MultipartFile file, Long studentId, String bizType, Long bizId) {
        CurrentUser user = requireWriter();
        studentClassService.requireStudentScope(studentId, AcademicYearUtils.current(), user);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "文件不能超过 10MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!ALLOWED.contains(contentType)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "仅支持 JPG/PNG/WebP/PDF");
        }
        String extension = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".pdf";
        };
        String stored = UUID.randomUUID().toString().replace("-", "") + extension;
        try {
            Path base = dir.toAbsolutePath().normalize();
            Files.createDirectories(base);
            Path target = base.resolve(stored).normalize();
            if (!target.startsWith(base)) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "非法文件路径");
            }
            byte[] bytes = file.getBytes();
            Files.write(target, bytes);
            SysFile entity = new SysFile();
            entity.setStudentId(studentId);
            entity.setBizType(TextUtils.isBlank(bizType) ? "OTHER" : bizType.trim().toUpperCase());
            entity.setBizId(bizId);
            entity.setOriginalName(safeName(file.getOriginalFilename()));
            entity.setStoredName(stored);
            entity.setContentType(contentType);
            entity.setSizeBytes(file.getSize());
            entity.setSha256(sha256(bytes));
            entity.setUploadedBy(user.userId());
            mapper.insert(entity);
            audit(entity, AuditLogService.ACTION_UPLOAD, "上传附件：" + entity.getOriginalName());
            return toVO(entity);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("附件保存失败 studentId={}", studentId, ex);
            throw new BusinessException(ResultCode.STORAGE_UNAVAILABLE, "附件保存失败");
        }
    }

    @Override
    public FileVO meta(Long id) {
        SysFile entity = requireReadable(id);
        return toVO(entity);
    }

    @Override
    public byte[] download(Long id) {
        SysFile entity = requireReadable(id);
        try {
            Path base = dir.toAbsolutePath().normalize();
            Path target = base.resolve(entity.getStoredName()).normalize();
            if (!target.startsWith(base)) {
                throw new BusinessException(ResultCode.PARAM_INVALID, "非法文件路径");
            }
            byte[] bytes = Files.readAllBytes(target);
            audit(entity, AuditLogService.ACTION_DOWNLOAD, "下载附件：" + entity.getOriginalName());
            return bytes;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.STORAGE_UNAVAILABLE, "附件不存在或不可读");
        }
    }

    private CurrentUser requireWriter() {
        CurrentUser user = UserContext.get();
        if (user == null || user.role() == null || !user.canReadBusinessData()) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "无权上传附件");
        }
        if (user.role() == com.college.growth.common.enums.UserRoleEnum.ADMIN) {
            throw new BusinessException(ResultCode.PERMISSION_DENIED, "管理员只读，不能上传附件");
        }
        return user;
    }

    private SysFile requireReadable(Long id) {
        CurrentUser user = UserContext.get();
        SysFile entity = id == null ? null : mapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING, "附件不存在");
        }
        studentClassService.requireStudentScope(entity.getStudentId(), AcademicYearUtils.current(), user);
        return entity;
    }

    private String safeName(String name) {
        String value = TextUtils.isBlank(name) ? "attachment" : name.trim();
        value = value.replace("\\", "_").replace("/", "_");
        return value.length() > 200 ? value.substring(value.length() - 200) : value;
    }

    private String sha256(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest(bytes)) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private void audit(SysFile entity, String action, String detail) {
        auditLogService.record(AuditLogService.BIZ_FILE, "FILE", entity.getId(), entity.getStudentId(),
                null, null, action, detail);
    }

    private FileVO toVO(SysFile entity) {
        FileVO vo = new FileVO();
        vo.setId(entity.getId());
        vo.setStudentId(entity.getStudentId());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setOriginalName(entity.getOriginalName());
        vo.setContentType(entity.getContentType());
        vo.setSizeBytes(entity.getSizeBytes());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
