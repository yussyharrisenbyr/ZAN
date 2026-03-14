package com.example.dianzan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dianzan.model.entity.Blog;
import com.example.dianzan.model.vo.AuthorStatsVO;
import com.example.dianzan.model.vo.HotBlogVO;
import com.example.dianzan.model.vo.UserProfileStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
* @author linfu
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2026-02-09 12:56:03
* @Entity generator.domain.Blog
*/
@Mapper
public interface BlogMapper extends BaseMapper<Blog> {
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
    List<AuthorStatsVO> selectAuthorStats(@Param("userIds") Collection<Long> userIds);
    List<HotBlogVO> selectWeeklyHotBlogs(@Param("since") Date since, @Param("limit") int limit);
    UserProfileStatsVO selectUserProfileStats(@Param("userId") Long userId);
    Long selectTotalThumbCount();
    Long selectDistinctAuthorCount();
}




