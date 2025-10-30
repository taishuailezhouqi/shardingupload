-- 文件分片表
CREATE TABLE file_chunk (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            file_md5 VARCHAR(32) NOT NULL,          -- 文件内容MD5
                            file_name VARCHAR(255) NOT NULL,        -- 原始文件名（仅用于显示）
                            file_size BIGINT NOT NULL,              -- 文件大小（额外验证）
                            chunk_index INT NOT NULL,               -- 分片索引
                            total_chunks INT NOT NULL,              -- 总分片数
                            chunk_path VARCHAR(500) NOT NULL,       -- 分片存储路径
                            user_id BIGINT NOT NULL,
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- 复合唯一约束：同一文件的同一分片只能存在一次
                            UNIQUE KEY uk_file_chunk (file_md5, chunk_index)
);

-- 文件索引表（用于秒传）
CREATE TABLE file_index (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            file_md5 VARCHAR(32) NOT NULL UNIQUE,   -- 文件内容MD5作为主键
                            original_name VARCHAR(255) NOT NULL,    -- 首次上传的文件名
                            file_size BIGINT NOT NULL,              -- 文件大小
                            oss_path VARCHAR(500) NOT NULL,         -- OSS存储路径
                            total_chunks INT NOT NULL,              -- 分片总数
                            first_upload_user BIGINT NOT NULL,      -- 首次上传用户
                            upload_count INT DEFAULT 1,             -- 上传次数（统计用）
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);