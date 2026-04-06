package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Echotik分类实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "echotik_category", schema = "db_cache")
public class EchotikCategory {

    @TableId(value = "category_id", type = IdType.INPUT)
    private String categoryId;

    private String region;
    private String parentId;
    private Integer level;
    private String nameEn;
    private String nameZh;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
