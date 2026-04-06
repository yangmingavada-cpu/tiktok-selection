package com.tiktok.selection;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TikTok选品系统启动类
 *
 * @author system
 * @date 2026/03/22
 */
@SpringBootApplication
@MapperScan("com.tiktok.selection.mapper")
@EnableCaching
@EnableScheduling
public class TiktokSelectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(TiktokSelectionApplication.class, args);
    }
}
