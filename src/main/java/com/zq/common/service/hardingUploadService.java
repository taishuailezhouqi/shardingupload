package com.zq.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zq.common.mapper.FileChunkMapper;
import com.zq.common.mapper.FileIndexMapper;
import com.zq.common.pojo.FileChunk;
import com.zq.common.pojo.FileIndex;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class hardingUploadService {

    private final FileIndexMapper fileIndexMapper;
    private final FileChunkMapper fileChunkMapper;

    private static final Logger log = LoggerFactory.getLogger(hardingUploadService.class);

    /**
     * 验证文件唯一性
     * 判断是否存在相同的文件，比较md5和文件大小
     */
    public boolean validateFileUniqueness(String fileMd5, String fileName, Long fileSize) {
        // 1. 基于MD5查询是否已存在
        FileIndex existingFile = fileIndexMapper.selectOne(new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getFileMd5, fileMd5));

        if (existingFile != null) {
            // 2. 验证文件大小是否匹配（双重保险）
            if (!existingFile.getFileSize().equals(fileSize)) {
                log.warn("MD5冲突警告: {} vs {}, 文件大小不匹配",
                        existingFile.getOriginalName(), fileName);
                return false;
            }

            // 3. 文件已存在，可以秒传
            log.info("文件已存在，支持秒传: {} -> {}", fileName, existingFile.getOssPath());
            return true;
        }

        return false;
    }


    /**
     * 检查文件上传状态
     */
    public Map<String, Object> checkFileStatus(String fileMd5) {
        Map<String, Object> result = new HashMap<>();

        // 1. 检查是否完全上传完成（秒传场景）
        FileIndex fileIndex = fileIndexMapper.selectOne(new LambdaQueryWrapper<FileIndex>().eq(FileIndex::getFileMd5, fileMd5));
        if (fileIndex != null) {
            result.put("uploaded", true);
            result.put("ossPath", fileIndex.getOssPath());
            result.put("canInstantUpload", true); // 支持秒传

            // 增加上传统计
            fileIndex.setUploadCount(fileIndex.getUploadCount() + 1);
            fileIndexMapper.updateById(fileIndex);

            return result;
        }

        // 2. 检查分片上传状态（断点续传场景）
        List<FileChunk> uploadedChunks = fileChunkMapper.selectList(new LambdaQueryWrapper<FileChunk>().eq(FileChunk::getFileMd5, fileMd5));

        if (uploadedChunks.isEmpty()) { //说明没有上传过
            result.put("uploaded", false);
            result.put("uploadedChunks", new ArrayList<>());
        } else {  //说明需要断点续传
            FileChunk firstChunk = uploadedChunks.get(0);
            boolean allUploaded = uploadedChunks.size() == firstChunk.getTotalChunks();

            result.put("uploaded", allUploaded);
            result.put("uploadedChunks", uploadedChunks.stream()
                    .map(FileChunk::getChunkIndex)
                    .collect(Collectors.toList()));
            result.put("totalChunks", firstChunk.getTotalChunks());
            result.put("fileName", firstChunk.getFileName());
        }

        return result;
    }

}
