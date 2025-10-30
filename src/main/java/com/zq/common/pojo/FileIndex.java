package com.zq.common.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文件索引表（用于秒传）
 */
@Data
@TableName("file_index")
public class FileIndex implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 文件内容MD5（唯一） */
    private String fileMd5;

    /** 首次上传的文件名 */
    private String originalName;

    /** 文件大小 */
    private Long fileSize;

    /** OSS存储路径 */
    private String ossPath;

    /** 分片总数 */
    private Integer totalChunks;

    /** 首次上传用户ID */
    private Long firstUploadUser;

    /** 上传次数（统计用） */
    private Integer uploadCount;

    /** 创建时间 */
    private LocalDateTime createTime;
}
