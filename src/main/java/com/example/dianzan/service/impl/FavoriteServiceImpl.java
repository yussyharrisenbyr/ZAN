package com.example.dianzan.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dianzan.exception.BusinessException;
import com.example.dianzan.exception.ErrorCode;
import com.example.dianzan.mapper.FavoriteMapper;
import com.example.dianzan.model.entity.Blog;
import com.example.dianzan.model.entity.Favorite;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.dto.favorite.DoFavoriteRequest;
import com.example.dianzan.model.vo.BlogPageVO;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.model.vo.FavoriteActionResponse;
import com.example.dianzan.service.BlogService;
import com.example.dianzan.service.FavoriteService;
import com.example.dianzan.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl extends ServiceImpl<FavoriteMapper, Favorite> implements FavoriteService {
    private static final int DEFAULT_FAVORITE_PAGE_SIZE = 15;
    private static final int MAX_FAVORITE_PAGE_SIZE = 30;
    private static final String FAVORITE_CURSOR_PREFIX = "favorite:";

    private final BlogService blogService;
    private final UserService userService;

    @Override
    public BlogPageVO listUserFavoriteBlogs(Long userId, String cursor, int size, HttpServletRequest request) {
        int normalizedSize = normalizeFavoritePageSize(size);
        FavoriteCursor cursorInfo = parseFavoriteCursor(cursor);
        Date cursorTime = cursorInfo == null ? null : cursorInfo.lastCreateTime();
        Long cursorId = cursorInfo == null ? null : cursorInfo.lastId();

        BlogPageVO result = new BlogPageVO();
        result.setSize(normalizedSize);
        result.setSort("favorite");
        result.setKeyword("");
        result.setHasMore(false);
        result.setNextCursor("");

        if (userId == null) {
            return result;
        }

        Long total = this.lambdaQuery().eq(Favorite::getUserId, userId).count();
        long safeTotal = total == null ? 0L : total;
        result.setTotal(safeTotal);
        if (safeTotal <= 0) {
            return result;
        }

        List<Favorite> favorites = this.lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .and(cursorId != null && cursorTime != null,
                        wrapper -> wrapper.lt(Favorite::getCreateTime, cursorTime)
                                .or(inner -> inner.eq(Favorite::getCreateTime, cursorTime)
                                        .lt(Favorite::getId, cursorId)))
                .orderByDesc(Favorite::getCreateTime)
                .orderByDesc(Favorite::getId)
                .last("limit " + (normalizedSize + 1))
                .list();

        boolean hasMore = favorites.size() > normalizedSize;
        List<Favorite> currentBatch = hasMore ? favorites.subList(0, normalizedSize) : favorites;
        List<BlogVO> favoriteBlogs = buildFavoriteBlogVOList(currentBatch, request);
        result.setList(favoriteBlogs);
        result.setHasMore(hasMore);
        if (hasMore && !currentBatch.isEmpty()) {
            result.setNextCursor(buildFavoriteCursor(currentBatch.getLast()));
        }
        return result;
    }

    @Override
    public FavoriteActionResponse doFavorite(DoFavoriteRequest request, HttpServletRequest httpServletRequest) {
        if (request == null || request.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客参数不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        Long blogId = request.getBlogId();
        if (hasFavorite(blogId, loginUser.getId())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已经收藏过这篇博客");
        }
        Blog blog = blogService.getById(blogId);
        if (blog == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "博客不存在");
        }
        Favorite favorite = new Favorite();
        favorite.setUserId(loginUser.getId());
        favorite.setBlogId(blogId);
        boolean saved = this.save(favorite);
        if (!saved) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "收藏失败，请稍后重试");
        }
        blogService.evictBlogDetailCache(blogId);
        FavoriteActionResponse response = new FavoriteActionResponse();
        response.setHasFavorite(true);
        response.setFavoriteCount(countFavorites(blogId));
        return response;
    }

    @Override
    public FavoriteActionResponse undoFavorite(DoFavoriteRequest request, HttpServletRequest httpServletRequest) {
        if (request == null || request.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客参数不能为空");
        }
        User loginUser = userService.getLoginUser(httpServletRequest);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        Long blogId = request.getBlogId();
        boolean removed = this.lambdaUpdate()
                .eq(Favorite::getUserId, loginUser.getId())
                .eq(Favorite::getBlogId, blogId)
                .remove();
        if (removed) {
            blogService.evictBlogDetailCache(blogId);
        }
        FavoriteActionResponse response = new FavoriteActionResponse();
        response.setHasFavorite(false);
        response.setFavoriteCount(countFavorites(blogId));
        return response;
    }

    @Override
    public boolean hasFavorite(Long blogId, Long userId) {
        if (blogId == null || userId == null) {
            return false;
        }
        return this.lambdaQuery()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getBlogId, blogId)
                .exists();
    }

    @Override
    public long countFavorites(Long blogId) {
        if (blogId == null) {
            return 0L;
        }
        Long total = this.baseMapper.selectFavoriteCountByBlogId(blogId);
        return total == null ? 0L : total;
    }

    private List<BlogVO> buildFavoriteBlogVOList(List<Favorite> favorites, HttpServletRequest request) {
        if (favorites == null || favorites.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> blogIds = favorites.stream().map(Favorite::getBlogId).toList();
        Map<Long, Favorite> favoriteMap = favorites.stream()
                .collect(Collectors.toMap(Favorite::getBlogId, favorite -> favorite, (left, right) -> left));
        Map<Long, Blog> blogMap = blogService.listByIds(blogIds).stream()
                .collect(Collectors.toMap(Blog::getId, blog -> blog, (left, right) -> left));
        List<Blog> orderedBlogs = blogIds.stream()
                .map(blogMap::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (orderedBlogs.isEmpty()) {
            return Collections.emptyList();
        }
        List<BlogVO> blogVOList = blogService.getBlogVOList(orderedBlogs, request);
        blogVOList.forEach(blogVO -> {
            Favorite favorite = favoriteMap.get(blogVO.getId());
            if (favorite != null) {
                blogVO.setFavoriteTime(favorite.getCreateTime());
            }
        });
        return blogVOList;
    }

    private int normalizeFavoritePageSize(int size) {
        if (size <= 0) {
            return DEFAULT_FAVORITE_PAGE_SIZE;
        }
        return Math.min(size, MAX_FAVORITE_PAGE_SIZE);
    }

    private FavoriteCursor parseFavoriteCursor(String cursor) {
        if (cursor == null || cursor.isBlank() || !cursor.startsWith(FAVORITE_CURSOR_PREFIX)) {
            return null;
        }
        try {
            String payload = cursor.substring(FAVORITE_CURSOR_PREFIX.length());
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) {
                return null;
            }
            long timeMillis = Long.parseLong(parts[0]);
            long id = Long.parseLong(parts[1]);
            return new FavoriteCursor(new Date(timeMillis), id);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String buildFavoriteCursor(Favorite favorite) {
        if (favorite == null || favorite.getId() == null || favorite.getCreateTime() == null) {
            return "";
        }
        return FAVORITE_CURSOR_PREFIX + favorite.getCreateTime().getTime() + ":" + favorite.getId();
    }

    private record FavoriteCursor(Date lastCreateTime, Long lastId) {
    }
}

