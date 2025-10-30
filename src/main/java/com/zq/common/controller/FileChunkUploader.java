package com.zq.common.controller;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

public class FileChunkUploader {

    public static void main(String[] args) throws Exception {
        File file = new File("E:\\test\\big_video.mp4");
        int chunkSize = 5 * 1024 * 1024; // 5MB 一片
        String fileMd5 = getFileMD5(file);
        long totalChunks = (file.length() + chunkSize - 1) / chunkSize;

        System.out.println("文件总分片数：" + totalChunks);

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[chunkSize];
            int len;
            int chunkIndex = 0;

            while ((len = fis.read(buffer)) != -1) {
                chunkIndex++;
                System.out.println("上传第 " + chunkIndex + " 片...");

                // 临时保存分片
                File chunkFile = new File("E:\\test\\chunk_" + chunkIndex);
                try (FileOutputStream fos = new FileOutputStream(chunkFile)) {
                    fos.write(buffer, 0, len);
                }

                uploadChunk(chunkFile, chunkIndex, totalChunks, fileMd5);

                // 删除临时文件
                chunkFile.delete();
            }
        }
    }

    private static void uploadChunk(File chunk, long chunkIndex, long totalChunks, String fileMd5) throws IOException {
        URL url = new URL("http://localhost:8080/upload/chunk");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundary12345");

        try (OutputStream out = conn.getOutputStream()) {
            String boundary = "----WebKitFormBoundary12345";

            // 普通字段
            String params = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"chunkIndex\"\r\n\r\n" + chunkIndex + "\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"chunkTotal\"\r\n\r\n" + totalChunks + "\r\n"
                    + "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"fileMd5\"\r\n\r\n" + fileMd5 + "\r\n";

            out.write(params.getBytes());

            // 文件部分
            out.write(("--" + boundary + "\r\n").getBytes());
            out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + chunk.getName() + "\"\r\n").getBytes());
            out.write(("Content-Type: application/octet-stream\r\n\r\n").getBytes());

            try (FileInputStream fis = new FileInputStream(chunk)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    out.write(buf, 0, len);
                }
            }

            out.write(("\r\n--" + boundary + "--\r\n").getBytes());
        }

        int responseCode = conn.getResponseCode();
        System.out.println("第 " + chunkIndex + " 片上传完成，响应码：" + responseCode);
        conn.disconnect();
    }

    private static String getFileMD5(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = fis.read(buf)) != -1) {
                md.update(buf, 0, len);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
