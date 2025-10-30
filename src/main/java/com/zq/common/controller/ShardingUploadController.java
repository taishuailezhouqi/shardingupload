package com.zq.common.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/shardingupload")
public class ShardingUploadController {






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
