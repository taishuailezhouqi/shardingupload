package com.zq.common.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件分片表
 */
@Builder
@Data
@TableName("file_chunk")
public class FileChunk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件内容MD5 */
    private String fileMd5;

    /** 原始文件名（仅用于显示） */
    private String fileName;

    /** 文件大小（额外验证） */
    private Long fileSize;

    /** 分片索引 */
    private Integer chunkIndex;

    /** 总分片数 */
    private Integer totalChunks;

    /** 分片存储路径 */
    private String chunkPath;

    /** 上传用户ID */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createTime;
}
