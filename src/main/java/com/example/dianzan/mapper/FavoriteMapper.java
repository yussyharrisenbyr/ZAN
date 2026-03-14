package com.example.dianzan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dianzan.model.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface FavoriteMapper extends BaseMapper<Favorite> {
    Long selectFavoriteCountByUserId(@Param("userId") Long userId);
    Long selectFavoriteCountByBlogId(@Param("blogId") Long blogId);
    List<Map<String, Object>> selectFavoriteCountByBlogIds(@Param("blogIds") Collection<Long> blogIds);
    List<Long> selectExistingFavoriteBlogIds(@Param("userId") Long userId, @Param("blogIds") Collection<Long> blogIds);
}

