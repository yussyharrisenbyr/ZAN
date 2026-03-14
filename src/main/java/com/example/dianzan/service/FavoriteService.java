package com.example.dianzan.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.dianzan.model.dto.favorite.DoFavoriteRequest;
import com.example.dianzan.model.entity.Favorite;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.FavoriteActionResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface FavoriteService extends IService<Favorite> {
    BlogPageVO listUserFavoriteBlogs(Long userId, String cursor, int size, HttpServletRequest request);
    FavoriteActionResponse doFavorite(DoFavoriteRequest request, HttpServletRequest httpServletRequest);
    FavoriteActionResponse undoFavorite(DoFavoriteRequest request, HttpServletRequest httpServletRequest);
    boolean hasFavorite(Long blogId, Long userId);
    long countFavorites(Long blogId);
}

