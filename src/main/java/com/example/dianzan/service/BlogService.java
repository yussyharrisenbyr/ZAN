package com.example.dianzan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dianzan.model.entity.Blog;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.model.vo.HotBlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author linfu
* @description 针对表【blog】的数据库操作Service
* @createDate 2026-02-09 12:56:03
*/
public interface BlogService extends IService<Blog> {
    BlogVO getBlogVOById(long blogId, HttpServletRequest request);
    BlogVO warmupAnonymousBlogDetailCache(long blogId);
    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
    BlogPageVO listUserBlogs(Long userId, String cursor, int size, String sort, String keyword, HttpServletRequest request);
    boolean saveBlog(Blog blog);
    void evictBlogDetailCache(Long blogId);
    List<HotBlogVO> listWeeklyHotBlogs(int limit);

}
