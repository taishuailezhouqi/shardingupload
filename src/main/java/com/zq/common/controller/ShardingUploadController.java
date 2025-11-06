package com.zq.common.controller;

import com.zq.common.pojo.FileChunk;
import com.zq.common.service.ShardingUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/shardingupload")
@RequiredArgsConstructor
public class ShardingUploadController {

    private final ShardingUploadService shardingUploadService;


    public static MultipartFile convert(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return new MockMultipartFile(
                    file.getName(),     // 表单字段名
                    file.getName(),     // 原始文件名
                    "application/octet-stream", // contentType，可根据实际文件类型修改
                    fis                 // 文件输入流
            );
        }
    }

    /**
     * 多线程测试分片上传
     */
    @GetMapping("/test")
    public String test() {
        File file1 = new File("E:/upload_chunks/ditieE57.zip.part1");
        File file2 = new File("E:/upload_chunks/ditieE57.zip.part2");
        File file3 = new File("E:/upload_chunks/ditieE57.zip.part3");
        File file4 = new File("E:/upload_chunks/ditieE57.zip.part4");
        File file5 = new File("E:/upload_chunks/ditieE57.zip.part5");
        File file6 = new File("E:/upload_chunks/ditieE57.zip.part6");
        List<File> files = Arrays.asList(file1, file2, file3, file4, file5, file6);
        for (int i = 0; i < files.size(); i++) {
            int finalI = i;
            int finalI1 = i;
            new Thread(() -> {
                try {
                    Map<String, Object> stringObjectMap = shardingUpload(convert(files.get(finalI)), "md7", "ditie.zip", 524288L, finalI1 + 1, 6, 1L);
                    Object message = stringObjectMap.get("message");
                    System.out.println(message.toString());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

        return "test";
    }


    /**
     * 单独的合并接口 - 前端在确认所有分片上传完成后调用
     */
    @PostMapping("/mergeFile")
    public Map<String, Object> mergeFile(@RequestParam("fileMd5") String fileMd5,
                                         @RequestParam("fileName") String fileName) {
        return shardingUploadService.mergeChunks(fileMd5, fileName);
    }


    /**
     * 查询上传进度
     */
    @GetMapping("/uploadProgress")
    public Map<String, Object> getUploadProgress(@RequestParam("fileMd5") String fileMd5) {
        return shardingUploadService.checkFileStatus(fileMd5);
    }

    /**
     * 使用分片上传、秒传、断点续传
     *
     * @param file           上传的分片文件
     * @param fileMd5        文件的唯一md5
     * @param fileName       文件名
     * @param fileSize       文件大小
     * @param fileChunkIndex 当前上传文件的索引
     * @param totalChunks    整个文件分为几个分片文件
     * @param userId         用户id
     */
    @PostMapping("/shardingUpload")
    public Map<String, Object> shardingUpload(@RequestParam("file") MultipartFile file,
                                              @RequestParam("fileMd5") String fileMd5,
                                              @RequestParam("fileName") String fileName,
                                              @RequestParam("fileSize") Long fileSize,
                                              @RequestParam("fileChunkIndex") Integer fileChunkIndex,
                                              @RequestParam("totalChunks") Integer totalChunks,
                                              @RequestParam("userId") Long userId
    ) throws IOException {

        try {
            FileChunk fileChunk = FileChunk.builder()
                    .fileMd5(fileMd5)
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .chunkIndex(fileChunkIndex)
                    .userId(userId)
                    .totalChunks(totalChunks).build();
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
            return result;
        } catch (IOException e) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "上传失败: " + e.getMessage());
            return errorResult;
        }
    }


    /**
     * 上传接口 先将文件分片（在没有前端传递的情况下）
     */
    @PostMapping("/splitSave")
    public String splitSave(@RequestParam("file") MultipartFile file) throws IOException {
        // 设置每片大小，这里是 20MB
        final int CHUNK_SIZE = 20 * 1024 * 1024;

        // 获取文件原始名
        String originalName = file.getOriginalFilename();
        // 保存路径（自己改）
        String basePath = "E:/upload_chunks/";
        File dir = new File(basePath);
        if (!dir.exists()) {
            if (dir.mkdirs()){
                log.info("创建文件分片目录成功:{}", basePath);
            }
        }

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
