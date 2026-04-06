package com.tiktok.selection.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预设包实体
 *
 * @author system
 * @date 2026/03/22
 */
@Data
@TableName(value = "preset_package", schema = "db_platform", autoResultMap = true)
public class PresetPackage {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String pkgCode;
    private String nameZh;
    private String nameEn;
    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> blockChain;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Object> tags;

    private Integer useCount;
    @TableField("is_active")
    private Boolean active;
    private Integer sortOrder;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
