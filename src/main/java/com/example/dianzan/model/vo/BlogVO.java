package com.example.dianzan.model.vo;

import lombok.Data;

import java.util.Date;
import java.util.List;


@Data
public class BlogVO {

    private Long id;

    private Long userId;
  
    /**  
     * 标题  
     */  
    private String title;  
  
    /**  
     * 封面  
     */  
    private String coverImg;  

    /**
     * 博客图片列表
     */
    private List<String> imageList;
  
    /**  
     * 内容  
     */  
    private String content;  
  
    /**  
     * 点赞数  
     */  
    private Integer thumbCount;  
  
    /**  
     * 创建时间  
     */  
    private Date createTime;

    /**
     * 点赞时间（点赞记录页使用）
     */
    private Date thumbTime;

    /**
     * 收藏时间（收藏记录页使用）
     */
    private Date favoriteTime;

    /**
     * 收藏数
     */
    private Long favoriteCount;
  
    /**  
     * 是否已点赞  
     */  
    private Boolean hasThumb;

    /**
     * 是否已收藏
     */
    private Boolean hasFavorite;

    private String authorName;

    private String authorAvatarUrl;

    private Long authorArticleCount;

    private Long authorLikeCount;

    private Long authorFavoriteCount;

}
