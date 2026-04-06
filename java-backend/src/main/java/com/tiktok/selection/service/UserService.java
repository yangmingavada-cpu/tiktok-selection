package com.tiktok.selection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tiktok.selection.entity.User;
import com.tiktok.selection.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 用户服务，提供用户查询与管理
 *
 * @author system
 * @date 2026/03/22
 */
@Service
@RequiredArgsConstructor
public class UserService extends ServiceImpl<UserMapper, User> {

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(
            getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email))
        );
    }

    public boolean emailExists(String email) {
        return count(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }

    public void updateLastLogin(String userId) {
        User user = new User();
        user.setId(userId);
        user.setLastLoginTime(LocalDateTime.now());
        updateById(user);
    }

    public IPage<User> pageUsers(int pageNum, int pageSize, String status, String role, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(User::getStatus, status);
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getEmail, keyword).or().like(User::getName, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }
}
