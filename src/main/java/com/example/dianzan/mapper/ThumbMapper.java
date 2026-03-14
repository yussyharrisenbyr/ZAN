package com.example.dianzan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dianzan.model.entity.Thumb;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ThumbMapper extends BaseMapper<Thumb> {

    /**
     * 批量插入点赞记录（自带重复忽略防线）
     */
    int insertIgnoreBatch(@Param("list") List<Thumb> list);

    Long selectThumbCountByUserId(@Param("userId") Long userId);
}