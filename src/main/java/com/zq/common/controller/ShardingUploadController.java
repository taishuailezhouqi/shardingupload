package com.zq.common.controller;

import com.zq.common.pojo.FileChunk;
import com.zq.common.service.ShardingUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/shardingupload")
@RequiredArgsConstructor
public class ShardingUploadController {

    private final ShardingUploadService shardingUploadService;


    /**
     * 使用分片上传、秒传、断点续传
     * @param file 上传的分片文件
     * @param fileMd5 文件的唯一md5
     * @param fileName 文件名
     * @param fileSize 文件大小
     * @param fileChunkIndex 当前上传文件的索引
     * @param totalChunks 整个文件分为几个分片文件
     * @param userId 用户id
     */
    @PostMapping("/shardingUpload")
    public Map<String, Object> shardingUpload(@RequestParam("file")MultipartFile file,
                                              @RequestParam("fileMd5")String fileMd5,
                                              @RequestParam("fileName")String fileName,
                                              @RequestParam("fileSize")Long fileSize,
                                              @RequestParam("fileChunkIndex")String fileChunkIndex,
                                              @RequestParam("totalChunks")Integer totalChunks,
                                              @RequestParam("userId")Long userId
    ) throws IOException {
        FileChunk fileChunk = FileChunk.builder().fileMd5(fileMd5).fileName(fileName).fileSize(fileSize).chunkIndex(Integer.parseInt(fileChunkIndex)).userId(userId).totalChunks(totalChunks).build();
        Map<String, Object> result = new HashMap<>();
        //检查文件唯一性  true为可秒传，false需要重新上传
        boolean isFileNameUnique = shardingUploadService.validateFileUniqueness(fileChunk.getFileMd5(), fileChunk.getFileName(), fileChunk.getFileSize());

        if (isFileNameUnique) {
            //放入oss等操作
            result.put("status", "success");
            result.put("message", "秒传成功");
            result.put("secondTransfer", true);
            return result;
        }
        //需要上传
        Map<String, Object> stringObjectMap = shardingUploadService.checkFileStatus(fileChunk.getFileMd5());
        boolean canInstantUpload = (boolean) stringObjectMap.get("canInstantUpload");
        if (canInstantUpload) {
            //放入oss等操作
            result.put("status", "success");
            result.put("message", "秒传成功");
            result.put("secondTransfer", true);
            return result;
        }
        Object uploadedChunks = stringObjectMap.get("uploadedChunks");
        if (Objects.isNull(uploadedChunks)) {
            //说明没有上传过  需要上传
            result = shardingUploadService.uploadChunk(file, fileChunk);
        } else {
            //说明有上传过，断点续传
            result = shardingUploadService.handleResumeUpload(file, fileChunk, (List<Integer>) uploadedChunks);
        }
        boolean uploaded = (boolean) result.get("uploaded");
        if (uploaded) {
            return shardingUploadService.mergeChunks(fileChunk.getFileMd5(), fileChunk.getFileName());
        } else {
            return result;
        }
    }


    /**
     * 上传接口 先将文件分片（在没有前端传递的情况下）
     */
    @PostMapping("/splitSave")
    public String splitSave(@RequestParam("file") MultipartFile file) throws IOException {
        // 设置每片大小，这里是 500MB
        final int CHUNK_SIZE = 500 * 1024 * 1024;

        // 获取文件原始名
        String originalName = file.getOriginalFilename();
        // 保存路径（自己改）
        String basePath = "E:/upload_chunks/";
        File dir = new File(basePath);
        if (!dir.exists()) dir.mkdirs();

        try (InputStream in = file.getInputStream()) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int len;
            int index = 1;

            // 循环分片保存
            while ((len = in.read(buffer)) != -1) {
                String chunkFileName = basePath + originalName + ".part" + index;
                try (FileOutputStream out = new FileOutputStream(chunkFileName)) {
                    out.write(buffer, 0, len);
                }
                System.out.println("保存分片：" + chunkFileName);
                index++;
            }
        }

        return "文件已分片保存完成！";
    }

}
