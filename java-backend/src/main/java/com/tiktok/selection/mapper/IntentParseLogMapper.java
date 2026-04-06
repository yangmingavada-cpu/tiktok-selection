package com.tiktok.selection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tiktok.selection.entity.IntentParseLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 意图解析日志 Mapper
 *
 * @author system
 * @date 2026/03/24
 */
@Mapper
public interface IntentParseLogMapper extends BaseMapper<IntentParseLog> {

    /**
     * 统计所有规划阶段累计消耗的 Token 数
     */
    @Select("SELECT COALESCE(SUM(llm_tokens_used), 0) FROM db_session.intent_parse_log")
    long sumTotalTokens();

    /**
     * 统计所有规划阶段累计 API 调用次数
     */
    @Select("SELECT COALESCE(SUM(api_calls), 0) FROM db_session.intent_parse_log")
    long sumTotalApiCalls();

    /**
     * 统计指定用户当月规划阶段消耗的 Token 数
     */
    @Select("SELECT COALESCE(SUM(llm_tokens_used), 0) FROM db_session.intent_parse_log " +
            "WHERE user_id = #{userId} AND create_time >= DATE_TRUNC('month', NOW())")
    long sumMonthlyTokensByUser(String userId);

    /**
     * 统计指定用户当月规划阶段 API 调用次数
     */
    @Select("SELECT COALESCE(SUM(api_calls), 0) FROM db_session.intent_parse_log " +
            "WHERE user_id = #{userId} AND create_time >= DATE_TRUNC('month', NOW())")
    long sumMonthlyApiCallsByUser(String userId);

    /**
     * 统计指定用户当月规划次数（含失败）
     */
    @Select("SELECT COUNT(*) FROM db_session.intent_parse_log " +
            "WHERE user_id = #{userId} AND create_time >= DATE_TRUNC('month', NOW())")
    long countMonthlyParsesByUser(String userId);
}
