package com.tiktok.selection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.tiktok.selection.common.R;
import com.tiktok.selection.dto.ConversationSnapshot;
import com.tiktok.selection.dto.request.SessionCreateRequest;
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

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
     * 导出会话结果为 Excel (.xlsx) 文件
     *
     * @param id       会话ID
     * @param response HttpServletResponse
     */
    @GetMapping("/{id}/export")
    public void exportExcel(@PathVariable String id, HttpServletResponse response) throws IOException {
        String userId = getCurrentUserId();
        SessionResponse session = sessionService.getSessionDetail(id, userId);

        Map<String, Object> currentView = session.getCurrentView();
        String title = session.getTitle() != null ? session.getTitle() : id;
        String filename = URLEncoder.encode(title + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("选品结果");

            if (currentView == null || !currentView.containsKey("data")) {
                Row row = sheet.createRow(0);
                row.createCell(0).setCellValue("暂无数据");
                workbook.write(response.getOutputStream());
                return;
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> dims = (List<Map<String, Object>>) currentView.get("dims");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) currentView.get("data");

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            List<String> keys;
            if (dims != null && !dims.isEmpty()) {
                keys = dims.stream().map(d -> String.valueOf(d.get("id"))).toList();
                // 写表头
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < dims.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(String.valueOf(dims.get(i).getOrDefault("label", dims.get(i).get("id"))));
                    cell.setCellStyle(headerStyle);
                }
            } else if (data != null && !data.isEmpty()) {
                keys = List.copyOf(data.get(0).keySet());
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < keys.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(keys.get(i));
                    cell.setCellStyle(headerStyle);
                }
            } else {
                Row row = sheet.createRow(0);
                row.createCell(0).setCellValue("暂无数据");
                workbook.write(response.getOutputStream());
                return;
            }

            // 写数据行
            if (data != null) {
                for (int r = 0; r < data.size(); r++) {
                    Row row = sheet.createRow(r + 1);
                    Map<String, Object> rowData = data.get(r);
                    for (int c = 0; c < keys.size(); c++) {
                        Cell cell = row.createCell(c);
                        Object val = rowData.get(keys.get(c));
                        if (val == null) {
                            cell.setCellValue("");
                        } else if (val instanceof Number num) {
                            cell.setCellValue(num.doubleValue());
                        } else {
                            cell.setCellValue(String.valueOf(val));
                        }
                    }
                }
            }

            // 自动列宽
            for (int i = 0; i < keys.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(response.getOutputStream());
        }
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
