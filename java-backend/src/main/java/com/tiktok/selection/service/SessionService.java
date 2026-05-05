package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.dto.ConversationSnapshot;
import com.tiktok.selection.dto.request.ExtraColCreateRequest;
import com.tiktok.selection.dto.request.ExtraColUpdateRequest;
import com.tiktok.selection.dto.request.SessionCellUpdateRequest;
import com.tiktok.selection.dto.request.SessionColUpdateRequest;
import com.tiktok.selection.dto.request.SessionCreateRequest;
import com.tiktok.selection.dto.request.SessionExportRequest;
import com.tiktok.selection.dto.request.SessionRowsDeleteRequest;
import com.tiktok.selection.dto.response.SessionResponse;
import com.tiktok.selection.entity.Session;
import com.tiktok.selection.entity.SessionData;
import com.tiktok.selection.entity.SessionStep;
import com.tiktok.selection.entity.UserPlan;
import com.tiktok.selection.engine.BlockOrchestrator;
import com.tiktok.selection.manager.SseEmitterManager;
import com.tiktok.selection.mapper.SessionDataMapper;
import com.tiktok.selection.mapper.SessionMapper;
import com.tiktok.selection.mapper.SessionStepMapper;
import com.tiktok.selection.mapper.UserPlanMapper;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.common.SessionStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 会话服务，负责会话生命周期管理
 *
 * @author system
 * @date 2026/03/22
 */
@Service
public class SessionService extends ServiceImpl<SessionMapper, Session> {

    private static final Logger logger = LoggerFactory.getLogger(SessionService.class);

    private final SessionDataMapper sessionDataMapper;
    private final SessionStepMapper sessionStepMapper;
    private final SseEmitterManager sseEmitterManager;
    private final BlockOrchestrator blockOrchestrator;
    private final ObjectMapper objectMapper;
    private final UserPlanMapper userPlanMapper;

    public SessionService(SessionDataMapper sessionDataMapper,
                          SessionStepMapper sessionStepMapper,
                          SseEmitterManager sseEmitterManager,
                          BlockOrchestrator blockOrchestrator,
                          ObjectMapper objectMapper,
                          UserPlanMapper userPlanMapper) {
        this.sessionDataMapper = sessionDataMapper;
        this.sessionStepMapper = sessionStepMapper;
        this.sseEmitterManager = sseEmitterManager;
        this.blockOrchestrator = blockOrchestrator;
        this.objectMapper = objectMapper;
        this.userPlanMapper = userPlanMapper;
    }

    /**
     * 创建会话及关联的空SessionData记录
     *
     * @param userId  用户ID
     * @param request 创建请求
     * @return 创建的会话实体
     */
    @Transactional(rollbackFor = Exception.class)
    public Session createSession(String userId, SessionCreateRequest request) {
        Session session = new Session();
        session.setUserId(userId);
        String title = request.getTitle();
        if (!StringUtils.hasText(title) && StringUtils.hasText(request.getSourceText())) {
            String src = request.getSourceText().trim();
            title = src.length() > 20 ? src.substring(0, 20) + "…" : src;
        }
        session.setTitle(title);
        session.setStatus(SessionStatusEnum.CREATED.getValue());
        session.setCurrentStep(0);
        session.setSourceType(request.getSourceType());
        session.setSourceText(request.getSourceText());
        session.setSourcePlanId(request.getSourcePlanId());
        session.setAgentThreadId(request.getAgentThreadId());
        session.setBlockChain(List.copyOf(request.getBlockChain()));
        session.setEchotikApiCalls(0);
        session.setLlmTotalTokens(0L);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        save(session);

        SessionData sessionData = new SessionData();
        sessionData.setSessionId(session.getId());
        sessionData.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.insert(sessionData);

        // 如果是从方案库执行（带 sourcePlanId），自动 +1 useCount + 更新 lastUsedTime
        // 这里集中做，PlanController.executePlan 不再重复，前端 plans.vue 直接调 createSession
        // 也能享受同样的统计行为
        bumpPlanUseCount(request.getSourcePlanId());

        logger.info("Session created: id={}, userId={}", session.getId(), userId);
        return session;
    }

    /**
     * 方案 useCount +1 + 更新 lastUsedTime，sourcePlanId 为空时跳过
     * 失败仅 warn，不影响主流程
     */
    private void bumpPlanUseCount(String sourcePlanId) {
        if (!StringUtils.hasText(sourcePlanId)) return;
        try {
            UserPlan plan = userPlanMapper.selectById(sourcePlanId);
            if (plan == null) return;
            plan.setUseCount(plan.getUseCount() == null ? 1 : plan.getUseCount() + 1);
            plan.setLastUsedTime(LocalDateTime.now());
            userPlanMapper.updateById(plan);
        } catch (Exception e) {
            logger.warn("Failed to bump plan useCount: planId={}, err={}", sourcePlanId, e.getMessage());
        }
    }

    /**
     * 获取会话详情，包含SessionData和步骤数
     *
     * @param sessionId 会话ID
     * @param userId    用户ID（用于验证归属）
     * @return 会话详情响应
     */
    public SessionResponse getSessionDetail(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权访问该会话");
        }

        SessionData sessionData = sessionDataMapper.selectById(sessionId);
        Long stepCount = sessionStepMapper.selectCount(
                new LambdaQueryWrapper<SessionStep>()
                        .eq(SessionStep::getSessionId, sessionId));

        return buildSessionResponse(session, sessionData, stepCount.intValue());
    }

    /**
     * 分页查询用户会话列表
     *
     * @param userId   用户ID
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param status   状态过滤（可选）
     * @return 分页结果
     */
    public IPage<Session> listSessions(String userId, Integer pageNum,
                                       Integer pageSize, String status, String context) {
        LambdaQueryWrapper<Session> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Session::getUserId, userId);
        if (StringUtils.hasText(status)) {
            String[] statuses = status.split(",");
            if (statuses.length == 1) {
                wrapper.eq(Session::getStatus, statuses[0].trim());
            } else {
                List<String> statusList = java.util.Arrays.stream(statuses)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
                wrapper.in(Session::getStatus, statusList);
            }
        }
        // 按视图独立过滤：对话历史和选品记录两边的"隐藏"flag 完全独立
        if ("chat".equals(context)) {
            // hidden_from_chat 列默认 false，老数据为 null 也视为 false
            wrapper.and(w -> w.eq(Session::getHiddenFromChat, false).or().isNull(Session::getHiddenFromChat));
        } else if ("records".equals(context)) {
            wrapper.and(w -> w.eq(Session::getHiddenFromRecords, false).or().isNull(Session::getHiddenFromRecords));
        }
        wrapper.orderByDesc(Session::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    /**
     * 软删除会话（需验证归属）
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     */
    public void removeSession(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权删除该会话");
        }
        removeById(sessionId);
        logger.info("Session removed: id={}, userId={}", sessionId, userId);
    }

    /**
     * 从"对话历史"侧栏隐藏（不影响选品记录页）
     * 与 hiddenFromRecords 完全独立，不触发 @TableLogic 软删
     */
    public void hideSessionFromChat(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权操作该会话");
        }
        Session update = new Session();
        update.setId(sessionId);
        update.setHiddenFromChat(true);
        update.setUpdateTime(LocalDateTime.now());
        updateById(update);
        logger.info("Session hidden from chat: id={}, userId={}", sessionId, userId);
    }

    /**
     * 从"选品记录"页隐藏（不影响对话历史侧栏）
     */
    public void hideSessionFromRecords(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权操作该会话");
        }
        Session update = new Session();
        update.setId(sessionId);
        update.setHiddenFromRecords(true);
        update.setUpdateTime(LocalDateTime.now());
        updateById(update);
        logger.info("Session hidden from records: id={}, userId={}", sessionId, userId);
    }

    /**
     * 重命名"对话历史"独立标题（不影响选品记录页 title）
     * @param chatTitle 新标题；null 或空字符串视为清空，前端将 fallback 到 title
     */
    public void updateChatTitle(String sessionId, String userId, String chatTitle) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权操作该会话");
        }
        String trimmed = chatTitle == null ? null : chatTitle.trim();
        if (trimmed != null && trimmed.length() > 255) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT, "对话标题最长 255");
        }
        // 用 UpdateWrapper 显式 set 才能写入 null（直接 setChatTitle(null) + updateById 会被 MyBatis-Plus 忽略）
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Session> uw =
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        uw.eq(Session::getId, sessionId)
          .set(Session::getChatTitle, (trimmed == null || trimmed.isEmpty()) ? null : trimmed)
          .set(Session::getUpdateTime, LocalDateTime.now());
        update(uw);
        logger.info("Session chat title updated: id={}, chatTitle={}", sessionId, trimmed);
    }

    /**
     * 启动会话执行：校验状态后异步调用编排器
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeSession(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权执行该会话");
        }
        if (!SessionStatusEnum.CREATED.getValue().equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT, "会话状态不允许执行，当前状态: " + session.getStatus());
        }

        session.setStatus(SessionStatusEnum.IN_PROGRESS.getValue());
        session.setStartTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        updateById(session);

        blockOrchestrator.executeAsync(session);
        logger.info("Session execution started: id={}", sessionId);
    }

    /**
     * 订阅SSE（委托给SseEmitterManager）
     * 注：EventSource不支持自定义Header，端点已设为公开，sessionId（UUID）作为访问凭证
     *
     * @param sessionId 会话ID
     * @return SseEmitter实例
     */
    public SseEmitter subscribeSse(String sessionId) {
        if (getById(sessionId) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        return sseEmitterManager.subscribe(sessionId);
    }

    /**
     * 继续执行已暂停的会话
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void resumeSession(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权操作该会话");
        }
        if (!SessionStatusEnum.PAUSED.getValue().equals(session.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "会话状态不允许继续，当前状态: " + session.getStatus());
        }

        session.setStatus(SessionStatusEnum.IN_PROGRESS.getValue());
        session.setUpdateTime(LocalDateTime.now());
        updateById(session);

        blockOrchestrator.resumeAsync(session);
        logger.info("Session resumed: id={}", sessionId);
    }

    /**
     * 取消正在执行或已暂停的会话
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     */
    public void cancelSession(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权操作该会话");
        }
        String status = session.getStatus();
        if (!SessionStatusEnum.IN_PROGRESS.getValue().equals(status)
                && !SessionStatusEnum.PAUSED.getValue().equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "会话状态不允许取消，当前状态: " + status);
        }

        session.setStatus(SessionStatusEnum.CANCELLED.getValue());
        session.setUpdateTime(LocalDateTime.now());
        updateById(session);

        sseEmitterManager.sendEvent(sessionId,
                com.tiktok.selection.dto.response.SseProgressEvent.fail(
                        sessionId, session.getCurrentStep(), null, "用户已取消执行"));
        logger.info("Session cancelled: id={}", sessionId);
    }

    /**
     * 查询会话步骤列表（需验证归属）
     *
     * @param sessionId 会话ID
     * @param userId    用户ID
     * @return 步骤列表，按seq升序
     */
    public List<SessionStep> listSteps(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权访问该会话");
        }
        return sessionStepMapper.selectList(
                new LambdaQueryWrapper<SessionStep>()
                        .eq(SessionStep::getSessionId, sessionId)
                        .orderByAsc(SessionStep::getSeq));
    }

    /**
     * 构建会话详情响应对象
     */
    @SuppressWarnings("unchecked")
    private SessionResponse buildSessionResponse(Session session,
                                                 SessionData sessionData,
                                                 Integer stepCount) {
        SessionResponse response = new SessionResponse();
        response.setId(session.getId());
        response.setUserId(session.getUserId());
        response.setTitle(session.getTitle());
        response.setChatTitle(session.getChatTitle());
        response.setAgentThreadId(session.getAgentThreadId());
        response.setStatus(session.getStatus());
        response.setCurrentStep(session.getCurrentStep());
        response.setSourceType(session.getSourceType());
        response.setSourceText(session.getSourceText());
        response.setMatchedPreset(session.getMatchedPreset());
        if (session.getBlockChain() != null) {
            List<Map<String, Object>> chain = new ArrayList<>();
            for (Object item : session.getBlockChain()) {
                if (item instanceof Map) {
                    chain.add((Map<String, Object>) item);
                }
            }
            response.setBlockChain(chain);
        }
        response.setEchotikApiCalls(session.getEchotikApiCalls());
        response.setLlmTotalTokens(session.getLlmTotalTokens());
        response.setStartTime(session.getStartTime());
        response.setCompleteTime(session.getCompleteTime());
        response.setCreateTime(session.getCreateTime());
        response.setUpdateTime(session.getUpdateTime());
        response.setStepCount(stepCount);
        response.setRemark(session.getRemark());
        response.setAuditResult(session.getAuditResult());
        response.setCompetitorAnalysis(session.getCompetitorAnalysis());

        // 解析 conversationSnapshot
        if (session.getConversationSnapshot() != null) {
            try {
                ConversationSnapshot snapshot = objectMapper.treeToValue(
                    session.getConversationSnapshot(),
                    ConversationSnapshot.class
                );
                response.setConversationSnapshot(snapshot);
            } catch (Exception e) {
                logger.warn("Failed to parse conversationSnapshot for session: {}, error: {}",
                           session.getId(), e.getMessage());
            }
        }

        if (sessionData != null) {
            response.setCurrentView(sessionData.getCurrentView());
            response.setUserExtraCols(sessionData.getUserExtraCols());
        }
        return response;
    }

    /**
     * 保存会话快照（由 Python 通过 API 调用）
     *
     * @param sessionId 会话ID
     * @param snapshot  对话快照
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveConversationSnapshot(String sessionId, ConversationSnapshot snapshot) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }

        // 转换为 JsonNode
        JsonNode jsonNode = objectMapper.valueToTree(snapshot);

        session.setConversationSnapshot(jsonNode);
        session.setUpdateTime(LocalDateTime.now());
        updateById(session);

        logger.info("保存会话快照成功: sessionId={}, messagesCount={}",
                   sessionId, snapshot.getMessages() != null ? snapshot.getMessages().size() : 0);
    }

    /**
     * 更新会话信息（部分字段）
     *
     * @param sessionId   会话ID
     * @param userId      用户ID（权限验证）
     * @param title       新标题（可选）
     * @param remark      新备注（可选）
     * @return 更新后的会话
     */
    public Session updateSessionInfo(String sessionId, String userId, String title, String remark) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权修改该会话");
        }

        boolean updated = false;
        if (StringUtils.hasText(title)) {
            session.setTitle(title.trim());
            updated = true;
        }
        if (remark != null) {  // 允许设置空字符串
            session.setRemark(remark.trim());
            updated = true;
        }

        if (updated) {
            session.setUpdateTime(LocalDateTime.now());
            updateById(session);
            logger.info("Session info updated: sessionId={}, userId={}", sessionId, userId);
        }

        return session;
    }

    // ============================================================
    // DataGrid 单元格编辑 + 用户增列 + 灵活导出
    // ============================================================

    /** 允许编辑的会话状态白名单 */
    private static final Set<String> EDITABLE_STATUS = Set.of(
            SessionStatusEnum.COMPLETED.getValue(),
            SessionStatusEnum.PAUSED.getValue(),
            SessionStatusEnum.FAILED.getValue(),
            SessionStatusEnum.CANCELLED.getValue()
    );

    /**
     * 编辑单元格（原始列或用户增列）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateSessionCell(String sessionId, String userId, SessionCellUpdateRequest req) {
        Session session = checkEditableSession(sessionId, userId);
        SessionData sd = sessionDataMapper.selectById(sessionId);
        if (sd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话数据不存在");
        }

        Map<String, Object> cv = sd.getCurrentView();
        Map<String, Object> extra = sd.getUserExtraCols();

        boolean isExtraCol = isUserExtraCol(extra, req.getField());
        if (isExtraCol) {
            extra = writeExtraCellValue(extra, req.getRowIndex(), req.getField(), req.getValue());
            sd.setUserExtraCols(extra);
        } else {
            writeCurrentViewCell(cv, req.getRowIndex(), req.getField(), req.getValue());
            // 不重新构造 cv，就地修改 data[rowIndex][field]，保留 outputType / dims / totalCount
            sd.setCurrentView(cv);
        }
        sd.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.updateById(sd);

        logger.info("Session cell updated: sessionId={}, rowIndex={}, field={}, isExtra={}",
                sessionId, req.getRowIndex(), req.getField(), isExtraCol);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("row", buildMergedRow(cv, extra, req.getRowIndex()));
        result.put("updateTime", sd.getUpdateTime());
        return result;
    }

    /**
     * 新增一个用户增列
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addExtraCol(String sessionId, String userId, ExtraColCreateRequest req) {
        checkEditableSession(sessionId, userId);
        if ("tag".equals(req.getType()) && (req.getOptions() == null || req.getOptions().isEmpty())) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT, "tag 类型必须提供 options");
        }

        SessionData sd = sessionDataMapper.selectById(sessionId);
        if (sd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话数据不存在");
        }

        Map<String, Object> extra = ensureExtraStructure(sd.getUserExtraCols());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cols = (List<Map<String, Object>>) extra.get("cols");

        String colId = "user_" + req.getType() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 4);
        Map<String, Object> newCol = new LinkedHashMap<>();
        newCol.put("id", colId);
        newCol.put("label", req.getLabel().trim());
        newCol.put("type", req.getType());
        if (req.getOptions() != null) {
            newCol.put("options", req.getOptions());
        }
        cols.add(newCol);

        sd.setUserExtraCols(extra);
        sd.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.updateById(sd);

        logger.info("Extra col added: sessionId={}, colId={}, label={}", sessionId, colId, req.getLabel());
        return newCol;
    }

    /**
     * 修改用户增列（label / options）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> renameExtraCol(String sessionId, String userId, String colId, ExtraColUpdateRequest req) {
        checkEditableSession(sessionId, userId);
        if (!colId.startsWith("user_")) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT, "只能修改用户增列");
        }

        SessionData sd = sessionDataMapper.selectById(sessionId);
        if (sd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话数据不存在");
        }

        Map<String, Object> target = renameExtraColInternal(sd, colId,
                StringUtils.hasText(req.getLabel()) ? req.getLabel().trim() : null,
                req.getOptions());

        sd.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.updateById(sd);

        logger.info("Extra col renamed: sessionId={}, colId={}", sessionId, colId);
        return target;
    }

    /**
     * 删除用户增列（同时清理所有行的对应值）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeExtraCol(String sessionId, String userId, String colId) {
        checkEditableSession(sessionId, userId);
        if (!colId.startsWith("user_")) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT, "只能删除用户增列");
        }

        SessionData sd = sessionDataMapper.selectById(sessionId);
        if (sd == null || sd.getUserExtraCols() == null) {
            return;
        }

        removeExtraColInternal(sd, colId);

        sd.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.updateById(sd);

        logger.info("Extra col removed: sessionId={}, colId={}", sessionId, colId);
    }

    // ============================================================
    // DataGrid 行删除 / 列删除 / 列重命名（原始列 + 用户增列统一入口）
    // ============================================================

    /**
     * 批量删除行
     * - 从 currentView.data 移除指定下标（从大到小删，避免 index shift）
     * - 重算 totalCount
     * - 重映射 userExtraCols.values 的 key（被删行的值丢弃，幸存行按偏移量 shift）
     */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> deleteSessionRows(String sessionId, String userId, SessionRowsDeleteRequest req) {
        checkEditableSession(sessionId, userId);
        SessionData sd = sessionDataMapper.selectById(sessionId);
        if (sd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话数据不存在");
        }

        Map<String, Object> cv = sd.getCurrentView();
        if (cv == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前视图为空");
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) cv.get("data");
        if (data == null) {
            data = new ArrayList<>();
        }

        // 1. 去重 + 边界校验
        TreeSet<Integer> toDeleteDesc = new TreeSet<>(Comparator.reverseOrder());
        for (Integer idx : req.getRowIndices()) {
            if (idx == null || idx < 0 || idx >= data.size()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "行下标越界: " + idx);
            }
            toDeleteDesc.add(idx);
        }
        if (toDeleteDesc.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "rowIndices 为空");
        }
        if (toDeleteDesc.size() >= data.size()) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT, "不能删除所有行");
        }

        // 2. 从大到小删（避免 index shift）
        int originalSize = data.size();
        for (Integer idx : toDeleteDesc) {
            data.remove((int) idx);
        }
        cv.put("data", data);
        cv.put("totalCount", data.size());
        sd.setCurrentView(cv);

        // 3. 重映射 userExtraCols.values 的 key
        Map<String, Object> extra = sd.getUserExtraCols();
        if (extra != null) {
            Map<String, Object> oldValues = (Map<String, Object>) extra.get("values");
            if (oldValues != null && !oldValues.isEmpty()) {
                // 从小到大的删除下标列表，用于计算每个幸存行的偏移量
                List<Integer> deletedAsc = new ArrayList<>(toDeleteDesc);
                Collections.reverse(deletedAsc);

                Map<String, Object> newValues = new LinkedHashMap<>();
                for (Map.Entry<String, Object> e : oldValues.entrySet()) {
                    int oldIdx;
                    try {
                        oldIdx = Integer.parseInt(e.getKey());
                    } catch (NumberFormatException ex) {
                        continue;
                    }
                    if (toDeleteDesc.contains(oldIdx)) {
                        continue; // 被删行的 values 丢弃
                    }
                    int shift = 0;
                    for (int d : deletedAsc) {
                        if (d < oldIdx) {
                            shift++;
                        } else {
                            break;
                        }
                    }
                    newValues.put(String.valueOf(oldIdx - shift), e.getValue());
                }
                extra.put("values", newValues);
                sd.setUserExtraCols(extra);
            }
        }

        sd.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.updateById(sd);

        logger.info("Session rows deleted: sessionId={}, deleted={}, remain={}",
                sessionId, toDeleteDesc.size(), data.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", data.size());
        result.put("deletedCount", originalSize - data.size());
        result.put("userExtraCols", sd.getUserExtraCols() != null ? sd.getUserExtraCols() : new LinkedHashMap<>());
        return result;
    }

    /**
     * 删除列（统一入口：原始列走 currentView.dims+data 清理，用户增列走 userExtraCols）
     */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> deleteSessionCol(String sessionId, String userId, String field) {
        checkEditableSession(sessionId, userId);
        SessionData sd = sessionDataMapper.selectById(sessionId);
        if (sd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话数据不存在");
        }

        Map<String, Object> extra = sd.getUserExtraCols();
        boolean isExtra = isUserExtraCol(extra, field);

        if (isExtra) {
            removeExtraColInternal(sd, field);
        } else {
            Map<String, Object> cv = sd.getCurrentView();
            if (cv == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "当前视图为空");
            }
            List<Map<String, Object>> dims = (List<Map<String, Object>>) cv.get("dims");
            if (dims == null || dims.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "列定义为空");
            }
            if (dims.size() <= 1) {
                throw new BusinessException(ErrorCode.INVALID_USER_INPUT, "不能删除最后一列");
            }
            boolean removed = dims.removeIf(d -> field.equals(d.get("id")));
            if (!removed) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "列不存在: " + field);
            }
            List<Map<String, Object>> data = (List<Map<String, Object>>) cv.get("data");
            if (data != null) {
                for (Map<String, Object> row : data) {
                    row.remove(field);
                }
            }
            cv.put("dims", dims);
            cv.put("data", data);
            sd.setCurrentView(cv);
        }

        sd.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.updateById(sd);

        logger.info("Session col deleted: sessionId={}, field={}, isExtra={}", sessionId, field, isExtra);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("isExtra", isExtra);
        return result;
    }

    /**
     * 重命名列（统一入口：原始列改 currentView.dims[i].label，用户增列改 userExtraCols.cols[i].label）
     */
    @Transactional(rollbackFor = Exception.class)
    @SuppressWarnings("unchecked")
    public Map<String, Object> renameSessionCol(String sessionId, String userId, String field, SessionColUpdateRequest req) {
        checkEditableSession(sessionId, userId);
        SessionData sd = sessionDataMapper.selectById(sessionId);
        if (sd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话数据不存在");
        }

        String newLabel = req.getLabel().trim();
        Map<String, Object> extra = sd.getUserExtraCols();
        boolean isExtra = isUserExtraCol(extra, field);

        if (isExtra) {
            renameExtraColInternal(sd, field, newLabel, null);
        } else {
            Map<String, Object> cv = sd.getCurrentView();
            if (cv == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "当前视图为空");
            }
            List<Map<String, Object>> dims = (List<Map<String, Object>>) cv.get("dims");
            if (dims == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "列定义为空");
            }
            Map<String, Object> target = null;
            for (Map<String, Object> d : dims) {
                if (field.equals(d.get("id"))) {
                    target = d;
                    break;
                }
            }
            if (target == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "列不存在: " + field);
            }
            target.put("label", newLabel);
            cv.put("dims", dims);
            sd.setCurrentView(cv);
        }

        sd.setUpdateTime(LocalDateTime.now());
        sessionDataMapper.updateById(sd);

        logger.info("Session col renamed: sessionId={}, field={}, isExtra={}", sessionId, field, isExtra);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("field", field);
        result.put("label", newLabel);
        result.put("isExtra", isExtra);
        return result;
    }

    /**
     * 灵活导出（按 视图过滤 + 列筛选 + 行筛选 + 排序 + 重命名）
     * 全部参数为空时回退到全量导出，向后兼容旧调用。
     */
    public void exportSessionExcelWithView(String sessionId, String userId,
                                           SessionExportRequest req,
                                           HttpServletResponse response) throws IOException {
        SessionResponse session = getSessionDetail(sessionId, userId);
        Map<String, Object> currentView = session.getCurrentView();
        Map<String, Object> extra = session.getUserExtraCols();

        // 1. 合并 cols（原始 + 用户增列）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> origDims = currentView != null
                ? (List<Map<String, Object>>) currentView.get("dims") : null;
        List<Map<String, Object>> mergedCols = new ArrayList<>();
        if (origDims != null) {
            mergedCols.addAll(origDims);
        }
        if (extra != null) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> extraCols = (List<Map<String, Object>>) extra.get("cols");
            if (extraCols != null) {
                mergedCols.addAll(extraCols);
            }
        }

        // 2. 合并行数据（原始行 + 增列值，按 rowIndex 索引）
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> origData = currentView != null
                ? (List<Map<String, Object>>) currentView.get("data") : null;
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> extraValues = extra != null
                ? (Map<String, Map<String, Object>>) extra.get("values") : null;

        List<Map<String, Object>> mergedRows = new ArrayList<>();
        if (origData != null) {
            for (int i = 0; i < origData.size(); i++) {
                Map<String, Object> row = new LinkedHashMap<>(origData.get(i));
                row.put("__originalIndex", i);
                if (extraValues != null) {
                    Map<String, Object> evRow = extraValues.get(String.valueOf(i));
                    if (evRow != null) {
                        row.putAll(evRow);
                    }
                }
                mergedRows.add(row);
            }
        }

        // 2.5 注入合成"商品链接"列：当 dims 包含 product_id 时，在第一个价格列前插入 product_link 列
        injectProductLinkColumn(mergedCols, mergedRows);

        // 3. 应用 rowIndices 过滤
        if (StringUtils.hasText(req.getRowIndices())) {
            Set<Integer> wanted = new HashSet<>();
            for (String s : req.getRowIndices().split(",")) {
                try { wanted.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
            }
            mergedRows.removeIf(row -> {
                Object idx = row.get("__originalIndex");
                return !(idx instanceof Integer) || !wanted.contains(idx);
            });
        }

        // 4. 应用 search 过滤（任意字段值包含关键字，case-insensitive）
        if (StringUtils.hasText(req.getSearch())) {
            String q = req.getSearch().toLowerCase();
            mergedRows.removeIf(row -> {
                for (Map.Entry<String, Object> e : row.entrySet()) {
                    if ("__originalIndex".equals(e.getKey())) continue;
                    Object v = e.getValue();
                    if (v != null && v.toString().toLowerCase().contains(q)) return false;
                }
                return true;
            });
        }

        // 5. 应用 order 排序
        if (StringUtils.hasText(req.getOrder())) {
            String[] parts = req.getOrder().split(":");
            String orderField = parts[0];
            boolean desc = parts.length > 1 && "desc".equalsIgnoreCase(parts[1]);
            mergedRows.sort((a, b) -> {
                Object va = a.get(orderField);
                Object vb = b.get(orderField);
                int cmp;
                if (va == null && vb == null) cmp = 0;
                else if (va == null) cmp = -1;
                else if (vb == null) cmp = 1;
                else if (va instanceof Number na && vb instanceof Number nb) {
                    cmp = Double.compare(na.doubleValue(), nb.doubleValue());
                } else {
                    cmp = va.toString().compareTo(vb.toString());
                }
                return desc ? -cmp : cmp;
            });
        }

        // 6. 应用 fields 列过滤
        List<Map<String, Object>> exportCols;
        if (StringUtils.hasText(req.getFields())) {
            Set<String> wantedFields = new HashSet<>(Arrays.asList(req.getFields().split(",")));
            // 去除空白
            wantedFields.removeIf(s -> s == null || s.trim().isEmpty());
            Set<String> trimmed = new HashSet<>();
            for (String f : wantedFields) trimmed.add(f.trim());
            exportCols = new ArrayList<>();
            for (Map<String, Object> col : mergedCols) {
                if (trimmed.contains(String.valueOf(col.get("id")))) {
                    exportCols.add(col);
                }
            }
        } else {
            exportCols = mergedCols;
        }

        // 7. 应用 renames 列名重命名
        Map<String, String> renameMap = new HashMap<>();
        if (StringUtils.hasText(req.getRenames())) {
            for (String pair : req.getRenames().split(",")) {
                String[] kv = pair.split(":", 2);
                if (kv.length == 2) {
                    renameMap.put(kv[0].trim(), kv[1].trim());
                }
            }
        }

        // 8. 准备文件名与响应头
        String format = StringUtils.hasText(req.getFormat()) ? req.getFormat().toLowerCase() : "xlsx";
        String title = session.getTitle() != null ? session.getTitle() : sessionId;
        String filename = URLEncoder.encode(title + "_" + LocalDate.now() + "." + format,
                StandardCharsets.UTF_8).replace("+", "%20");
        String contentType = "csv".equals(format)
                ? "text/csv;charset=utf-8"
                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + filename);

        // 9. 输出
        if ("csv".equals(format)) {
            writeCsv(response.getOutputStream(), exportCols, mergedRows, renameMap);
        } else {
            writeXlsx(response.getOutputStream(), exportCols, mergedRows, renameMap);
        }
    }

    // ──── Private helpers ────────────────────────────────────────

    private static final Set<String> PRICE_COL_IDS = Set.of("min_price", "max_price", "spu_avg_price");
    private static final String PRODUCT_LINK_TEMPLATE =
            "https://www.tiktok.com/view/product/%s?share_region=%s&utm_source=copy";

    /**
     * 当列定义包含 product_id 时，向 mergedCols 注入合成"商品链接"列（位于第一个价格列前），
     * 并为每行根据 product_id + region 计算链接值。
     */
    private void injectProductLinkColumn(List<Map<String, Object>> mergedCols,
                                         List<Map<String, Object>> mergedRows) {
        boolean hasProductId = mergedCols.stream()
                .anyMatch(c -> "product_id".equals(c.get("id")));
        if (!hasProductId) return;

        Map<String, Object> linkCol = new LinkedHashMap<>();
        linkCol.put("id", "product_link");
        linkCol.put("label", "商品链接");
        linkCol.put("type", "string");

        int priceIdx = -1;
        for (int i = 0; i < mergedCols.size(); i++) {
            if (PRICE_COL_IDS.contains(String.valueOf(mergedCols.get(i).get("id")))) {
                priceIdx = i;
                break;
            }
        }
        if (priceIdx >= 0) {
            mergedCols.add(priceIdx, linkCol);
        } else {
            mergedCols.add(linkCol);
        }

        for (Map<String, Object> row : mergedRows) {
            Object pid = row.get("product_id");
            // region 经 translateForDisplay 已被翻成中文，优先取 region_code 兜底字段
            Object region = row.get("region_code");
            if (region == null) region = row.get("region");
            if (pid != null && region != null) {
                row.put("product_link", String.format(PRODUCT_LINK_TEMPLATE, pid, region));
            }
        }
    }

    /** 校验会话归属 + 状态白名单，返回会话实体 */
    private Session checkEditableSession(String sessionId, String userId) {
        Session session = getById(sessionId);
        if (session == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "会话不存在: " + sessionId);
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ACCESS_UNAUTHORIZED, "无权修改该会话");
        }
        if (!EDITABLE_STATUS.contains(session.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                    "当前状态不可编辑: " + session.getStatus());
        }
        return session;
    }

    /**
     * 从 SessionData.userExtraCols 中移除一个增列定义，并清理所有行里该列的 value。
     * 只改内存里的 sd，不做持久化 / 不校验权限。
     */
    @SuppressWarnings("unchecked")
    private void removeExtraColInternal(SessionData sd, String colId) {
        Map<String, Object> extra = sd.getUserExtraCols();
        if (extra == null) return;
        List<Map<String, Object>> cols = (List<Map<String, Object>>) extra.get("cols");
        if (cols != null) {
            cols.removeIf(c -> colId.equals(c.get("id")));
        }
        Map<String, Map<String, Object>> values = (Map<String, Map<String, Object>>) extra.get("values");
        if (values != null) {
            for (Map<String, Object> rowMap : values.values()) {
                rowMap.remove(colId);
            }
        }
        sd.setUserExtraCols(extra);
    }

    /**
     * 修改 SessionData.userExtraCols 的某个增列定义。
     * label 或 options 为 null 表示不动该字段。
     * 只改内存里的 sd，不做持久化 / 不校验权限。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> renameExtraColInternal(SessionData sd, String colId, String label, List<String> options) {
        Map<String, Object> extra = sd.getUserExtraCols();
        if (extra == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户增列不存在");
        }
        List<Map<String, Object>> cols = (List<Map<String, Object>>) extra.get("cols");
        Map<String, Object> target = null;
        if (cols != null) {
            for (Map<String, Object> col : cols) {
                if (colId.equals(col.get("id"))) {
                    target = col;
                    break;
                }
            }
        }
        if (target == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未找到列: " + colId);
        }
        if (label != null) {
            target.put("label", label);
        }
        if (options != null) {
            target.put("options", options);
        }
        sd.setUserExtraCols(extra);
        return target;
    }

    /** 判断 field 是否属于用户增列 */
    @SuppressWarnings("unchecked")
    private boolean isUserExtraCol(Map<String, Object> extra, String field) {
        if (extra == null) return false;
        List<Map<String, Object>> cols = (List<Map<String, Object>>) extra.get("cols");
        if (cols == null) return false;
        for (Map<String, Object> col : cols) {
            if (field.equals(col.get("id"))) return true;
        }
        return false;
    }

    /** 就地修改 currentView.data[rowIndex][field]，按 dim.type 标准化 value */
    @SuppressWarnings("unchecked")
    private void writeCurrentViewCell(Map<String, Object> cv, int rowIndex, String field, Object value) {
        if (cv == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "currentView 为空");
        }
        List<Map<String, Object>> data = (List<Map<String, Object>>) cv.get("data");
        if (data == null || rowIndex >= data.size() || rowIndex < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "rowIndex 越界: " + rowIndex);
        }
        List<Map<String, Object>> dims = (List<Map<String, Object>>) cv.get("dims");
        Map<String, Object> targetDim = null;
        if (dims != null) {
            for (Map<String, Object> dim : dims) {
                if (field.equals(dim.get("id"))) {
                    targetDim = dim;
                    break;
                }
            }
        }
        if (targetDim == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未找到列: " + field);
        }
        Object normalized = normalizeValueByDimType(value, String.valueOf(targetDim.get("type")));
        data.get(rowIndex).put(field, normalized);
    }

    /** 写入用户增列的单元格值，返回更新后的 extra Map */
    @SuppressWarnings("unchecked")
    private Map<String, Object> writeExtraCellValue(Map<String, Object> extra, int rowIndex, String field, Object value) {
        Map<String, Object> result = ensureExtraStructure(extra);
        // 校验 field 在 cols 列表
        List<Map<String, Object>> cols = (List<Map<String, Object>>) result.get("cols");
        Map<String, Object> targetCol = null;
        for (Map<String, Object> col : cols) {
            if (field.equals(col.get("id"))) {
                targetCol = col;
                break;
            }
        }
        if (targetCol == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "未找到用户增列: " + field);
        }
        // 校验 tag 类型 value 必须在 options 内（允许 null/空字符串清空）
        if ("tag".equals(targetCol.get("type")) && value != null && !"".equals(value)) {
            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) targetCol.get("options");
            if (options != null && !options.contains(String.valueOf(value))) {
                throw new BusinessException(ErrorCode.INVALID_USER_INPUT,
                        "tag 值不在可选项内: " + value);
            }
        }

        Map<String, Map<String, Object>> values = (Map<String, Map<String, Object>>) result.get("values");
        String key = String.valueOf(rowIndex);
        Map<String, Object> rowMap = values.computeIfAbsent(key, k -> new LinkedHashMap<>());
        if (value == null || "".equals(value)) {
            rowMap.remove(field);
        } else {
            rowMap.put(field, value);
        }
        return result;
    }

    /** 确保 extra 是 { cols: [], values: {} } 的合法结构（懒初始化） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> ensureExtraStructure(Map<String, Object> extra) {
        Map<String, Object> result = extra != null ? extra : new LinkedHashMap<>();
        if (!(result.get("cols") instanceof List)) {
            result.put("cols", new ArrayList<Map<String, Object>>());
        }
        if (!(result.get("values") instanceof Map)) {
            result.put("values", new LinkedHashMap<String, Map<String, Object>>());
        }
        return result;
    }

    /** 按 dim.type 把 value 标准化 */
    private Object normalizeValueByDimType(Object value, String type) {
        if (value == null) return null;
        if ("number".equals(type) || "percent".equals(type) || "score".equals(type)) {
            if (value instanceof Number) return ((Number) value).doubleValue();
            try {
                return Double.parseDouble(String.valueOf(value).replace(",", "").trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.INVALID_USER_INPUT, "数值无效: " + value);
            }
        }
        return String.valueOf(value);
    }

    /** 构造一个合并了原始列与增列的行 Map（用于 PATCH 响应） */
    @SuppressWarnings("unchecked")
    private Map<String, Object> buildMergedRow(Map<String, Object> cv, Map<String, Object> extra, int rowIndex) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (cv != null) {
            List<Map<String, Object>> data = (List<Map<String, Object>>) cv.get("data");
            if (data != null && rowIndex >= 0 && rowIndex < data.size()) {
                result.putAll(data.get(rowIndex));
            }
        }
        if (extra != null) {
            Map<String, Map<String, Object>> values = (Map<String, Map<String, Object>>) extra.get("values");
            if (values != null) {
                Map<String, Object> ev = values.get(String.valueOf(rowIndex));
                if (ev != null) result.putAll(ev);
            }
        }
        return result;
    }

    /** 写 xlsx（复用 SessionController 老 export 的代码风格） */
    private void writeXlsx(OutputStream out, List<Map<String, Object>> cols,
                           List<Map<String, Object>> rows, Map<String, String> renameMap) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("选品结果");

            if (cols.isEmpty()) {
                sheet.createRow(0).createCell(0).setCellValue("暂无数据");
                workbook.write(out);
                return;
            }

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 表头
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                Map<String, Object> col = cols.get(i);
                String colId = String.valueOf(col.get("id"));
                String displayLabel = renameMap.getOrDefault(colId,
                        String.valueOf(col.getOrDefault("label", colId)));
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(displayLabel);
                cell.setCellStyle(headerStyle);
            }

            // 数据
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Map<String, Object> rowData = rows.get(r);
                for (int c = 0; c < cols.size(); c++) {
                    String colId = String.valueOf(cols.get(c).get("id"));
                    Object val = rowData.get(colId);
                    Cell cell = row.createCell(c);
                    if (val == null) {
                        cell.setCellValue("");
                    } else if (val instanceof Number num) {
                        cell.setCellValue(num.doubleValue());
                    } else {
                        cell.setCellValue(String.valueOf(val));
                    }
                }
            }

            for (int i = 0; i < cols.size(); i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
        }
    }

    /** 写 CSV（UTF-8 BOM + RFC4180 引号） */
    private void writeCsv(OutputStream out, List<Map<String, Object>> cols,
                          List<Map<String, Object>> rows, Map<String, String> renameMap) throws IOException {
        // UTF-8 BOM 让 Excel 正确识别中文
        out.write(0xEF);
        out.write(0xBB);
        out.write(0xBF);

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            // 表头
            for (int i = 0; i < cols.size(); i++) {
                if (i > 0) pw.print(',');
                Map<String, Object> col = cols.get(i);
                String colId = String.valueOf(col.get("id"));
                String displayLabel = renameMap.getOrDefault(colId,
                        String.valueOf(col.getOrDefault("label", colId)));
                pw.print(escapeCsv(displayLabel));
            }
            pw.println();

            // 数据
            for (Map<String, Object> rowData : rows) {
                for (int c = 0; c < cols.size(); c++) {
                    if (c > 0) pw.print(',');
                    String colId = String.valueOf(cols.get(c).get("id"));
                    Object val = rowData.get(colId);
                    pw.print(escapeCsv(val == null ? "" : String.valueOf(val)));
                }
                pw.println();
            }
            pw.flush();
        }
    }

    /** RFC4180 CSV 字段转义 */
    private String escapeCsv(String s) {
        if (s == null) return "";
        boolean needQuote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
