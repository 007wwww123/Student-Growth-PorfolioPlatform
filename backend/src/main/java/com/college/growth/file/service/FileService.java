package com.college.growth.file.service;

import com.college.growth.file.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

/** 附件/文件服务（B-6）。 */
public interface FileService {

    FileVO upload(MultipartFile file, Long studentId, String bizType, Long bizId);

    FileVO meta(Long id);

    byte[] download(Long id);
}
