package com.tiktok.selection.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Session 更新请求（部分更新）
 *
 * @author system
 * @date 2026/04/02
 */
@Data
public class SessionUpdateRequest {

    /**
     * 任务标题（200字符以内）
     */
    @Size(max = 200, message = "标题最多200字符")
    private String title;

    /**
     * 备注说明（500字符以内）
     */
    @Size(max = 500, message = "备注最多500字符")
    private String remark;
}
