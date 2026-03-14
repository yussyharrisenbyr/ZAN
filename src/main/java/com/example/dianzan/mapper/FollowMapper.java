package com.example.dianzan.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.dianzan.model.entity.Follow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FollowMapper extends BaseMapper<Follow> {

    int deleteByFollowerAndFollowee(@Param("followerId") Long followerId,
                                    @Param("followeeId") Long followeeId);

    List<Long> selectFolloweeIds(@Param("followerId") Long followerId);

    List<Long> selectFollowerIds(@Param("followeeId") Long followeeId);

    List<Long> selectExistingFolloweeIds(@Param("followerId") Long followerId,
                                         @Param("candidateIds") List<Long> candidateIds);

    List<Long> selectExistingFollowerIds(@Param("followeeId") Long followeeId,
                                         @Param("candidateIds") List<Long> candidateIds);

    Long selectFolloweeCount(@Param("followerId") Long followerId);

    Long selectFollowerCount(@Param("followeeId") Long followeeId);

    Long countFolloweeUsers(@Param("followerId") Long followerId,
                            @Param("keyword") String keyword,
                            @Param("viewerId") Long viewerId,
                            @Param("mutualOnly") boolean mutualOnly);

    List<Long> selectFolloweeIdsPage(@Param("followerId") Long followerId,
                                     @Param("keyword") String keyword,
                                     @Param("viewerId") Long viewerId,
                                     @Param("mutualOnly") boolean mutualOnly,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    Long countFollowerUsers(@Param("followeeId") Long followeeId,
                            @Param("keyword") String keyword,
                            @Param("viewerId") Long viewerId,
                            @Param("mutualOnly") boolean mutualOnly);

    List<Long> selectFollowerIdsPage(@Param("followeeId") Long followeeId,
                                     @Param("keyword") String keyword,
                                     @Param("viewerId") Long viewerId,
                                     @Param("mutualOnly") boolean mutualOnly,
                                     @Param("offset") int offset,
                                     @Param("size") int size);
}

