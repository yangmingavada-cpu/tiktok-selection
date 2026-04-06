package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.selection.entity.UserTier;
import com.tiktok.selection.mapper.UserTierMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 用户等级服务
 *
 * @author system
 * @date 2026/03/22
 */
@Service
public class UserTierService extends ServiceImpl<UserTierMapper, UserTier> {

    public List<UserTier> listActive() {
        return list(new LambdaQueryWrapper<UserTier>()
                .eq(UserTier::getActive, true)
                .orderByAsc(UserTier::getSortOrder));
    }

    public Optional<UserTier> findByName(String name) {
        return Optional.ofNullable(
            getOne(new LambdaQueryWrapper<UserTier>().eq(UserTier::getName, name))
        );
    }

    public Optional<UserTier> findDefaultTier() {
        return findByName("free");
    }
}
