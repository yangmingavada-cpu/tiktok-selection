package com.tiktok.selection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.ConversationSnapshot;
import com.tiktok.selection.dto.request.ExtraColCreateRequest;
import com.tiktok.selection.dto.request.ExtraColUpdateRequest;
import com.tiktok.selection.dto.request.SessionCellUpdateRequest;
import com.tiktok.selection.dto.request.SessionColUpdateRequest;
import com.tiktok.selection.dto.request.SessionCreateRequest;
import com.tiktok.selection.dto.request.SessionExportRequest;
import com.tiktok.selection.dto.request.SessionRowsDeleteRequest;
import com.tiktok.selection.dto.response.SessionListResponse;
import com.tiktok.selection.dto.response.SessionResponse;
import com.tiktok.selection.dto.response.SessionStepResponse;
import com.tiktok.selection.entity.Session;
import com.tiktok.selection.entity.SessionStep;
import com.tiktok.selection.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 会话控制器，提供会话CRUD、执行、SSE订阅及步骤查询接口
 *
 * @author system
 * @date 2026/03/22
 */
@Validated
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * 创建会话
     *
     * @param request 创建请求
     * @return 会话详情
     */
    @PostMapping
    public R<SessionResponse> createSession(@Valid @RequestBody SessionCreateRequest request) {
        String userId = getCurrentUserId();
        Session session = sessionService.createSession(userId, request);
        SessionResponse response = sessionService.getSessionDetail(session.getId(), userId);
        return R.ok(response);
    }

    /**
     * 分页查询会话列表
     *
     * @param pageNum  页码，默认1
     * @param pageSize 每页条数，默认20
     * @param status   状态过滤（可选）
     * @return 分页会话列表
     */
    @GetMapping
    public R<IPage<SessionListResponse>> listSessions(
            @RequestParam(defaultValue = "1") @Min(1) Integer pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize,
            @RequestParam(required = false) String status) {
        String userId = getCurrentUserId();
        IPage<Session> sessionPage = sessionService.listSessions(userId, pageNum, pageSize, status);

        IPage<SessionListResponse> responsePage = sessionPage.convert(this::convertToListResponse);
        return R.ok(responsePage);
    }

    /**
     * 获取会话详情
     *
     * @param id 会话ID
     * @return 会话详情
     */
    @GetMapping("/{id}")
    public R<SessionResponse> getSession(@PathVariable String id) {
        String userId = getCurrentUserId();
        return R.ok(sessionService.getSessionDetail(id, userId));
    }

    /**
     * 删除会话（软删除）
     *
     * @param id 会话ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    public R<Void> removeSession(@PathVariable String id) {
        String userId = getCurrentUserId();
        sessionService.removeSession(id, userId);
        return R.ok();
    }

    /**
     * 更新会话信息（部分更新）
     *
     * @param id      会话ID
     * @param request 更新请求（title、remark 可选）
     * @return 更新后的会话
     */
    @PatchMapping("/{id}")
    public R<SessionResponse> updateSession(@PathVariable String id,
                                            @RequestBody @Valid com.tiktok.selection.dto.request.SessionUpdateRequest request) {
        String userId = getCurrentUserId();
        Session session = sessionService.updateSessionInfo(id, userId, request.getTitle(), request.getRemark());

        // 构造响应（简化版，只包含基本字段）
        SessionResponse response = new SessionResponse();
        response.setId(session.getId());
        response.setUserId(session.getUserId());
        response.setTitle(session.getTitle());
        response.setStatus(session.getStatus());
        response.setSourceText(session.getSourceText());
        response.setSourceType(session.getSourceType());
        response.setSourcePlanId(session.getSourcePlanId());
        response.setCurrentStep(session.getCurrentStep());
        response.setEchotikApiCalls(session.getEchotikApiCalls());
        response.setLlmTotalTokens(session.getLlmTotalTokens());
        response.setCreateTime(session.getCreateTime());
        response.setUpdateTime(session.getUpdateTime());

        return R.ok(response);
    }

    /**
     * 启动会话执行
     *
     * @param id 会话ID
     * @return 执行启动提示
     */
    @PostMapping("/{id}/execute")
    public R<String> executeSession(@PathVariable String id) {
        String userId = getCurrentUserId();
        sessionService.executeSession(id, userId);
        return R.ok("执行已启动");
    }

    /**
     * 订阅会话执行进度（SSE）
     *
     * @param id 会话ID
     * @return SseEmitter
     */
    @GetMapping(value = "/{id}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeSse(@PathVariable String id) {
        return sessionService.subscribeSse(id);
    }

    /**
     * 导出会话结果（支持 xlsx / csv，可按视图过滤、列筛选、排序、行选）
     * 所有 query 参数都为空时回退到全量导出，向后兼容旧调用。
     *
     * @param id       会话ID
     * @param req      导出参数（format / fields / order / search / rowIndices / renames）
     * @param response HttpServletResponse
     */
    @GetMapping("/{id}/export")
    public void exportExcel(@PathVariable String id,
                            @ModelAttribute SessionExportRequest req,
                            HttpServletResponse response) throws IOException {
        sessionService.exportSessionExcelWithView(id, getCurrentUserId(), req, response);
    }

    /**
     * 编辑单元格（原始列或用户增列）
     *
     * @param id  会话ID
     * @param req 单元格更新请求
     * @return 更新后的整行数据 + 更新时间
     */
    @PatchMapping("/{id}/cells")
    public R<Map<String, Object>> updateCell(@PathVariable String id,
                                             @Valid @RequestBody SessionCellUpdateRequest req) {
        return R.ok(sessionService.updateSessionCell(id, getCurrentUserId(), req));
    }

    /**
     * 新增用户列（备注/标签）
     *
     * @param id  会话ID
     * @param req 增列请求
     * @return 新增的列定义
     */
    @PostMapping("/{id}/extra-cols")
    public R<Map<String, Object>> addExtraCol(@PathVariable String id,
                                              @Valid @RequestBody ExtraColCreateRequest req) {
        return R.ok(sessionService.addExtraCol(id, getCurrentUserId(), req));
    }

    /**
     * 修改用户列（重命名 / 改 options）
     *
     * @param id    会话ID
     * @param colId 列ID（必须以 user_ 前缀）
     * @param req   更新请求
     * @return 更新后的列定义
     */
    @PatchMapping("/{id}/extra-cols/{colId}")
    public R<Map<String, Object>> renameExtraCol(@PathVariable String id,
                                                 @PathVariable String colId,
                                                 @Valid @RequestBody ExtraColUpdateRequest req) {
        return R.ok(sessionService.renameExtraCol(id, getCurrentUserId(), colId, req));
    }

    /**
     * 删除用户列（同时清理所有行的对应值）
     *
     * @param id    会话ID
     * @param colId 列ID（必须以 user_ 前缀）
     * @return 空响应
     */
    @DeleteMapping("/{id}/extra-cols/{colId}")
    public R<Void> removeExtraCol(@PathVariable String id, @PathVariable String colId) {
        sessionService.removeExtraCol(id, getCurrentUserId(), colId);
        return R.ok();
    }

    /**
     * 批量删除行
     * 采用 POST :delete 风格：DELETE 不能带 body，大批量行选中时 query string 会超长
     *
     * @param id  会话ID
     * @param req 要删除的行下标列表
     * @return 剩余 totalCount + deletedCount + 重映射后的 userExtraCols
     */
    @PostMapping("/{id}/rows:delete")
    public R<Map<String, Object>> deleteRows(@PathVariable String id,
                                             @Valid @RequestBody SessionRowsDeleteRequest req) {
        return R.ok(sessionService.deleteSessionRows(id, getCurrentUserId(), req));
    }

    /**
     * 删除列（统一入口：原始列 + 用户增列）
     * - 原始列：从 currentView.dims 移除，同时清理每行该字段的值
     * - 用户增列：从 userExtraCols.cols 移除，同时清理 values 里的对应 key
     *
     * @param id    会话ID
     * @param field 列字段 id
     * @return { field, isExtra }
     */
    @DeleteMapping("/{id}/cols/{field}")
    public R<Map<String, Object>> deleteCol(@PathVariable String id, @PathVariable String field) {
        return R.ok(sessionService.deleteSessionCol(id, getCurrentUserId(), field));
    }

    /**
     * 重命名列（统一入口：原始列 + 用户增列）
     * - 原始列：改 currentView.dims[i].label
     * - 用户增列：改 userExtraCols.cols[i].label
     *
     * @param id    会话ID
     * @param field 列字段 id
     * @param req   新 label
     * @return { field, label, isExtra }
     */
    @PatchMapping("/{id}/cols/{field}")
    public R<Map<String, Object>> renameCol(@PathVariable String id,
                                            @PathVariable String field,
                                            @Valid @RequestBody SessionColUpdateRequest req) {
        return R.ok(sessionService.renameSessionCol(id, getCurrentUserId(), field, req));
    }

    /**
     * 继续执行已暂停的会话
     *
     * @param id 会话ID
     * @return 操作结果
     */
    @PostMapping("/{id}/resume")
    public R<String> resumeSession(@PathVariable String id) {
        String userId = getCurrentUserId();
        sessionService.resumeSession(id, userId);
        return R.ok("已继续执行");
    }

    /**
     * 取消执行中或已暂停的会话
     *
     * @param id 会话ID
     * @return 操作结果
     */
    @PostMapping("/{id}/cancel")
    public R<String> cancelSession(@PathVariable String id) {
        String userId = getCurrentUserId();
        sessionService.cancelSession(id, userId);
        return R.ok("已取消执行");
    }

    /**
     * 查询会话步骤列表
     *
     * @param id 会话ID
     * @return 步骤列表
     */
    @GetMapping("/{id}/steps")
    public R<List<SessionStepResponse>> listSteps(@PathVariable String id) {
        String userId = getCurrentUserId();
        List<SessionStep> steps = sessionService.listSteps(id, userId);
        List<SessionStepResponse> responseList = steps.stream()
                .map(this::convertToStepResponse)
                .toList();
        return R.ok(responseList);
    }

    /**
     * 保存会话快照（由 Python AI 服务调用）
     *
     * @param id       会话ID
     * @param snapshot 对话快照
     * @return 空响应
     */
    @PostMapping("/{id}/conversation-snapshot")
    public R<Void> saveConversationSnapshot(@PathVariable String id,
                                            @RequestBody @Valid ConversationSnapshot snapshot) {
        sessionService.saveConversationSnapshot(id, snapshot);
        return R.ok();
    }

    /**
     * 从SecurityContext获取当前用户ID
     *
     * @return 用户ID
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getName();
    }

    /**
     * 将Session实体转换为列表响应DTO
     *
     * @param session 会话实体
     * @return 列表响应DTO
     */
    private SessionListResponse convertToListResponse(Session session) {
        SessionListResponse response = new SessionListResponse();
        response.setId(session.getId());
        response.setTitle(session.getTitle());
        response.setStatus(session.getStatus());
        response.setSourceType(session.getSourceType());
        response.setCurrentStep(session.getCurrentStep());
        response.setEchotikApiCalls(session.getEchotikApiCalls());
        response.setLlmTotalTokens(session.getLlmTotalTokens());
        response.setStartTime(session.getStartTime());
        response.setCompleteTime(session.getCompleteTime());
        response.setCreateTime(session.getCreateTime());
        return response;
    }

    /**
     * 将SessionStep实体转换为步骤响应DTO
     *
     * @param step 步骤实体
     * @return 步骤响应DTO
     */
    private SessionStepResponse convertToStepResponse(SessionStep step) {
        SessionStepResponse response = new SessionStepResponse();
        response.setId(step.getId());
        response.setSeq(step.getSeq());
        response.setBlockId(step.getBlockId());
        response.setLabel(step.getLabel());
        response.setStatus(step.getStatus());
        response.setInputCount(step.getInputCount());
        response.setOutputCount(step.getOutputCount());
        response.setEchotikApiCalls(step.getEchotikApiCalls());
        response.setLlmTotalTokens(step.getLlmTotalTokens());
        response.setDurationMs(step.getDurationMs());
        response.setCreateTime(step.getCreateTime());
        return response;
    }
}
