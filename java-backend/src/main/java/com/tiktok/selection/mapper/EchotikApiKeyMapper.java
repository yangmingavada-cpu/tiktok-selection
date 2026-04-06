package com.tiktok.selection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tiktok.selection.entity.EchotikApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EchotikApiKeyMapper extends BaseMapper<EchotikApiKey> {

    /**
     * 原子扣减密钥余量并更新最后使用时间，余量最小扣至 0。
     * 使用 UPDATE...SET 确保并发安全，无需先查后改。
     */
    @Update("UPDATE db_platform.echotik_api_key " +
            "SET remaining_calls = GREATEST(0, remaining_calls - #{calls}), " +
            "    last_used_time  = NOW() " +
            "WHERE id = #{keyId} AND is_active = TRUE")
    int decrementRemainingCalls(@Param("keyId") String keyId, @Param("calls") int calls);
}
