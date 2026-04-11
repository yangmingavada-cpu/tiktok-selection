package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户增列创建请求
 * 用于 POST /sessions/{id}/extra-cols
 *
 * @author system
 * @date 2026/04/10
 */
@Data
public class ExtraColCreateRequest {

    /**
     * 列名（用户可见）
     */
    @NotBlank(message = "列名不能为空")
    @Size(max = 50, message = "列名最多 50 字符")
    private String label;

    /**
     * 列类型：string | tag
     */
    @NotBlank(message = "列类型不能为空")
    @Pattern(regexp = "string|tag", message = "列类型只支持 string 或 tag")
    private String type;

    /**
     * tag 类型的可选项；type=tag 时必填且非空
     */
    private List<String> options;
}
