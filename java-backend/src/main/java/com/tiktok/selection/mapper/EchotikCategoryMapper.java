package com.tiktok.selection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tiktok.selection.entity.EchotikCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * Echotik分类Mapper接口
 *
 * @author system
 * @date 2026/03/22
 */
@Mapper
public interface EchotikCategoryMapper extends BaseMapper<EchotikCategory> {

    @Select("SELECT category_id, region, parent_id, level, name_en, name_zh, create_time, update_time FROM db_cache.echotik_category WHERE region = #{region} ORDER BY level, category_id")
    List<EchotikCategory> listByRegion(String region);

    @Select("SELECT category_id, region, parent_id, level, name_en, name_zh, create_time, update_time FROM db_cache.echotik_category WHERE region = #{region} AND name_zh = #{nameZh}")
    List<EchotikCategory> listByRegionAndName(@Param("region") String region, @Param("nameZh") String nameZh);
}
