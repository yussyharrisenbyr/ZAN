package com.example.dianzan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dianzan.model.entity.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {
    List<Comment> selectPageRoots(@Param("blogId") Long blogId, @Param("offset") int offset, @Param("size") int size);
    List<Comment> selectByRootIds(@Param("rootIds") List<Long> rootIds);
    long countRoots(@Param("blogId") Long blogId);
}
