package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiktok.selection.dto.ConversationSnapshot;
import com.tiktok.selection.dto.request.SessionCreateRequest;
import com.tiktok.selection.dto.response.SessionResponse;
import com.tiktok.selection.entity.Session;
import com.tiktok.selection.entity.SessionData;
import com.tiktok.selection.entity.SessionStep;
import com.tiktok.selection.engine.BlockOrchestrator;
import com.tiktok.selection.manager.SseEmitterManager;
import com.tiktok.selection.mapper.SessionDataMapper;
import com.tiktok.selection.mapper.SessionMapper;
import com.tiktok.selection.mapper.SessionStepMapper;
import com.tiktok.selection.common.BusinessException;
import com.tiktok.selection.common.ErrorCode;
import com.tiktok.selection.common.SessionStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    public SessionService(SessionDataMapper sessionDataMapper,
                          SessionStepMapper sessionStepMapper,
                          SseEmitterManager sseEmitterManager,
                          BlockOrchestrator blockOrchestrator,
                          ObjectMapper objectMapper) {
        this.sessionDataMapper = sessionDataMapper;
        this.sessionStepMapper = sessionStepMapper;
        this.sseEmitterManager = sseEmitterManager;
        this.blockOrchestrator = blockOrchestrator;
        this.objectMapper = objectMapper;
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

        logger.info("Session created: id={}, userId={}", session.getId(), userId);
        return session;
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
                                       Integer pageSize, String status) {
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
}
