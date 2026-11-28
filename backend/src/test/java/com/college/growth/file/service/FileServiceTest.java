package com.college.growth.file.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.college.growth.common.context.CurrentUser;
import com.college.growth.common.context.UserContext;
import com.college.growth.common.enums.UserRoleEnum;
import com.college.growth.common.exception.BusinessException;
import com.college.growth.common.log.service.AuditLogService;
import com.college.growth.common.org.service.StudentClassService;
import com.college.growth.common.result.ResultCode;
import com.college.growth.file.entity.SysFile;
import com.college.growth.file.mapper.SysFileMapper;
import com.college.growth.file.service.impl.FileServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/** 附件：类型限制、管理员只读。 */
@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    static {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysFile.class);
    }

    @Mock private SysFileMapper mapper;
    @Mock private StudentClassService studentClassService;
    @Mock private AuditLogService auditLogService;

    @TempDir java.nio.file.Path dir;

    private FileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileServiceImpl(mapper, studentClassService, auditLogService, dir.toString());
        UserContext.set(new CurrentUser(1001L, UserRoleEnum.STU, false));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("only image/pdf accepted")
    void rejectTextFile() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload(file, 1001L, "OTHER", null));
        assertEquals(ResultCode.PARAM_INVALID.getCode(), ex.getResultCode().getCode());
    }

    @Test
    @DisplayName("admin is read-only and cannot upload")
    void adminCannotUpload() {
        UserContext.set(new CurrentUser(9003L, UserRoleEnum.ADMIN, true));
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.upload(file, 1001L, "BASIC_PHOTO", null));
        assertEquals(ResultCode.PERMISSION_DENIED.getCode(), ex.getResultCode().getCode());
    }
}
