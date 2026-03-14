package com.example.dianzan.service;

import com.example.dianzan.model.dto.thumb.DoThumbRequest;
import com.example.dianzan.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.ThumbActionResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author linfu
* @description 针对表【thumb】的数据库操作Service
* @createDate 2026-02-09 12:56:22
*/
public interface ThumbService extends IService<Thumb> {
    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    ThumbActionResponse doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return {@link Boolean }
     */
    ThumbActionResponse undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
    Boolean hasThumb(Long blogId, Long userId);

    default BlogPageVO listUserThumbBlogs(Long userId, String cursor, int size, HttpServletRequest request) {
        throw new UnsupportedOperationException("listUserThumbBlogs is not implemented");
    }

}
