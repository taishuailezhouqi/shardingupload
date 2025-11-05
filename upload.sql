-- 文件分片表
CREATE TABLE file_chunk (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT comment "主键",
                            file_md5 VARCHAR(32) NOT NULL comment "文件内容MD5",          -- 文件内容MD5
                            file_name VARCHAR(255) NOT NULL comment "原始文件名",        -- 原始文件名（仅用于显示）
                            file_size BIGINT NOT NULL comment "文件大小",              -- 文件大小（额外验证）
                            chunk_index INT NOT NULL comment "分片索引",               -- 分片索引
                            total_chunks INT NOT NULL comment "总分片数",              -- 总分片数
                            chunk_path VARCHAR(500) NOT NULL comment "分片存储路径",       -- 分片存储路径
                            user_id BIGINT NOT NULL comment "用户id",
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP comment "创建时间",

    -- 复合唯一约束：同一文件的同一分片只能存在一次
                            UNIQUE KEY uk_file_chunk (file_md5, chunk_index)
);

-- 文件索引表（用于秒传）
CREATE TABLE file_index (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT comment "主键",
                            file_md5 VARCHAR(32) NOT NULL UNIQUE comment "文件内容MD5",   -- 文件内容MD5作为主键
                            original_name VARCHAR(255) NOT NULL comment "首次上传的文件名",    -- 首次上传的文件名
                            file_size BIGINT NOT NULL comment "文件大小",              -- 文件大小
                            oss_path VARCHAR(500) NOT NULL comment "OSS存储路径",         -- OSS存储路径
                            total_chunks INT NOT NULL comment "分片总数",              -- 分片总数
                            first_upload_user BIGINT NOT NULL comment "首次上传用户",      -- 首次上传用户
                            upload_count INT DEFAULT 1 comment "上传次数",             -- 上传次数（统计用）
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP comment "创建时间"
);