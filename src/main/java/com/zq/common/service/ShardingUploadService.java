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
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class ShardingUploadService {

    private final FileIndexMapper fileIndexMapper;
    private final FileChunkMapper fileChunkMapper;

    private static final Logger log = LoggerFactory.getLogger(ShardingUploadService.class);

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
            result.put("uploadedChunks", null);
            result.put("canInstantUpload", false);
        } else {  //说明需要断点续传
            FileChunk firstChunk = uploadedChunks.get(0);
            boolean allUploaded = uploadedChunks.size() == firstChunk.getTotalChunks();

            result.put("uploaded", allUploaded);
            result.put("uploadedChunks", uploadedChunks.stream()
                    .map(FileChunk::getChunkIndex)
                    .collect(Collectors.toList()));
            result.put("totalChunks", firstChunk.getTotalChunks());
            result.put("fileName", firstChunk.getFileName());
            result.put("canInstantUpload", false);
        }

        return result;
    }

    /**
     * 处理断点续传
     */
    public Map<String, Object> handleResumeUpload(MultipartFile file, FileChunk fileChunk, List<Integer> uploadedChunks) throws IOException {
        // 检查当前分片是否已上传
        if (uploadedChunks.contains(fileChunk.getChunkIndex())) {
            Map<String, Object> result = new HashMap<>();
            if (uploadedChunks.size() == fileChunk.getTotalChunks()){
                result.put("status", "success");
                result.put("message", "所有分片上传完成，准备合并");
                result.put("uploaded", true);
                result.put("uploadedChunks", uploadedChunks);
                return result;
            }
            result.put("status", "success");
            result.put("message", "该分片已上传，跳过");
            result.put("uploaded", false);
            result.put("uploadedChunks", uploadedChunks);
            return result;
        }

        // 上传当前分片
        return uploadChunk(file, fileChunk);
    }


    /**
     * 上传分片
     */
    public Map<String, Object> uploadChunk(MultipartFile file, FileChunk fileChunk) throws IOException {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 保存分片文件到本地
            String chunkPath = saveChunkToLocal(file, fileChunk);
            fileChunk.setChunkPath(chunkPath);
            fileChunk.setCreateTime(java.time.LocalDateTime.now());

            // 2. 保存分片记录到数据库
            fileChunkMapper.insert(fileChunk);

            // 3. 检查是否所有分片都已上传完成
            List<FileChunk> uploadedChunks = fileChunkMapper.selectList(
                    new LambdaQueryWrapper<FileChunk>().eq(FileChunk::getFileMd5, fileChunk.getFileMd5())
            );

            boolean isComplete = uploadedChunks.size() == fileChunk.getTotalChunks();

            result.put("status", "success");
            result.put("message", "分片上传成功");
            result.put("uploaded", isComplete);
            result.put("chunkIndex", fileChunk.getChunkIndex());
            result.put("totalChunks", fileChunk.getTotalChunks());
            result.put("uploadedChunks", uploadedChunks.stream()
                    .map(FileChunk::getChunkIndex)
                    .collect(Collectors.toList()));

            if (isComplete) {
                result.put("message", "所有分片上传完成，可以合并");
                result.put("uploaded", true);
            }

        } catch (Exception e) {
            log.error("分片上传失败: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "分片上传失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 保存分片文件到本地
     */
    private String saveChunkToLocal(MultipartFile file, FileChunk fileChunk) throws IOException {
        // 创建上传目录
        String uploadDir = "E:/upload_chunks/" + fileChunk.getFileMd5() + "/";
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            boolean mkdirs = dir.mkdirs();
            if (mkdirs) {
                log.info("创建分片文件夹成功: {}", uploadDir);
            }
        }

        // 分片文件名
        String chunkFileName = fileChunk.getFileMd5() + "_chunk_" + fileChunk.getChunkIndex();
        String chunkPath = uploadDir + chunkFileName;

        // 保存分片文件
        File chunkFile = new File(chunkPath);
        file.transferTo(chunkFile);

        log.info("分片保存成功: {}", chunkPath);
        return chunkPath;
    }

    /**
     * 合并分片
     */
    public Map<String, Object> mergeChunks(String fileMd5, String fileName) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 获取所有分片
            List<FileChunk> chunks = fileChunkMapper.selectList(
                    new LambdaQueryWrapper<FileChunk>()
                            .eq(FileChunk::getFileMd5, fileMd5)
                            .orderByAsc(FileChunk::getChunkIndex)
            );

            if (chunks.isEmpty()) {
                result.put("status", "error");
                result.put("message", "未找到分片文件");
                return result;
            }

            FileChunk firstChunk = chunks.get(0);
            if (chunks.size() != firstChunk.getTotalChunks()) {
                result.put("status", "error");
                result.put("message", "分片不完整，无法合并");
                return result;
            }

            // 2. 合并文件
            String mergedFilePath = mergeChunkFiles(chunks, fileName);

            // 3. 验证合并后的文件MD5
//            if (!validateMergedFile(mergedFilePath, fileMd5)) {
//                result.put("status", "error");
//                result.put("message", "文件合并后MD5验证失败");
//                return result;
//            }

            // 4. 保存文件索引记录
            FileIndex fileIndex = new FileIndex();
            fileIndex.setFileMd5(fileMd5);
            fileIndex.setOriginalName(fileName);
            fileIndex.setFileSize(firstChunk.getFileSize());
            fileIndex.setOssPath(mergedFilePath); // 这里可以改为OSS路径
            fileIndex.setUploadCount(1);
            fileIndex.setTotalChunks(firstChunk.getTotalChunks());
            fileIndex.setCreateTime(java.time.LocalDateTime.now());
            fileIndex.setFirstUploadUser(firstChunk.getUserId());
            fileIndexMapper.insert(fileIndex);

            // 5. 清理分片文件和记录
            cleanupChunks(chunks);

            result.put("status", "success");
            result.put("message", "文件合并成功");
            result.put("uploaded", true);
            result.put("filePath", mergedFilePath);

        } catch (Exception e) {
            log.error("文件合并失败: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "文件合并失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 合并分片文件
     */
    private String mergeChunkFiles(List<FileChunk> chunks, String fileName) throws IOException {
        String mergedDir = "E:/upload_merged/" + System.currentTimeMillis() ;
        File dir = new File(mergedDir);
        if (!dir.exists()) {
            boolean mkdirs = dir.mkdirs();
            if (mkdirs){
                log.info("创建文件夹成功: {}", mergedDir);
            }
        }

        String mergedFilePath = mergedDir + "/" + fileName;

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(mergedFilePath))) {
            for (FileChunk chunk : chunks) {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(chunk.getChunkPath()))) {
                    byte[] buffer = new byte[1024 * 1024]; // 1MB
                    int len;
                    while ((len = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, len);
                    }
                }
            }
        }

        return mergedFilePath;
    }


    /**
     * 验证合并后的文件MD5
     */
    private boolean validateMergedFile(String filePath, String expectedMd5) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            try (FileInputStream fis = new FileInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, len);
                }
            }

            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }

            return expectedMd5.contentEquals(sb);
        } catch (Exception e) {
            log.error("MD5验证失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 清理分片文件和数据库记录
     */
    private void cleanupChunks(List<FileChunk> chunks) {
        for (FileChunk chunk : chunks) {
            // 删除分片文件
            File chunkFile = new File(chunk.getChunkPath());
            if (chunkFile.exists()) {
                boolean delete = chunkFile.delete();
                if (delete){
                    log.info("删除分片文件成功: {}", chunk.getChunkPath());
                }
            }

            // 删除数据库记录
//            fileChunkMapper.deleteById(chunk.getId());
        }

        // 删除分片目录（如果为空）
        if (!chunks.isEmpty()) {
            String chunkDir = chunks.get(0).getChunkPath().substring(0, chunks.get(0).getChunkPath().lastIndexOf("/"));
            File dir = new File(chunkDir);
            if (dir.exists() && dir.isDirectory() && dir.list().length == 0) {
                dir.delete();
            }
        }
    }
}
