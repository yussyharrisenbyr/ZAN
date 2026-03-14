package com.example.dianzan.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.dianzan.exception.ErrorCode;
import com.example.dianzan.constant.UserConstant;
import com.example.dianzan.exception.BusinessException;
import com.example.dianzan.mapper.BlogMapper;
import com.example.dianzan.mapper.FavoriteMapper;
import com.example.dianzan.mapper.FollowMapper;
import com.example.dianzan.mapper.ThumbMapper;
import com.example.dianzan.model.dto.UserProfileUpdateRequest;
import com.example.dianzan.model.entity.User;
import com.example.dianzan.model.vo.UserProfileVO;
import com.example.dianzan.model.vo.UserProfileStatsVO;
import com.example.dianzan.service.UserService;
import com.example.dianzan.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
* @author linfu
* @description 针对表【user】的数据库操作Service实现
* @createDate 2026-02-09 12:56:25
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    private static final String SALT = "dianzan";
    private static final String DEFAULT_AVATAR_BG = "#1e80ff";
    private static final int ACCOUNT_LENGTH = 11;
    private static final int MIN_AGE = 1;
    private static final int MAX_AGE = 150;
    private static final int MAX_BIO_LENGTH = 200;
    private static final int MAX_AVATAR_URL_LENGTH = 1024;

    @Resource
    private BlogMapper blogMapper;
    @Resource
    private ThumbMapper thumbMapper;
    @Resource
    private FollowMapper followMapper;
    @Resource
    private FavoriteMapper favoriteMapper;

    @Override
    public User getLoginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (User) session.getAttribute(UserConstant.LOGIN_USER);
    }

    @Override
    public Long userRegister(String userAccount, String username, String password, String checkPassword) {
        userAccount = StringUtils.trim(userAccount);
        username = StringUtils.trim(username);
        if (StringUtils.isAnyBlank(userAccount, username, password, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() != ACCOUNT_LENGTH || !StringUtils.isNumeric(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号必须是 11 位纯数字");
        }
        if (username.length() < 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名过短");
        }
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码过短");
        }
        if (!password.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        synchronized (this) {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.count(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已存在");
            }
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUsername(username);
            user.setPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败");
            }
            return user.getId();
        }
    }

    @Override
    public User userLogin(String userAccount, String password, HttpServletRequest request) {
        userAccount = StringUtils.trim(userAccount);
        if (StringUtils.isAnyBlank(userAccount, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() != ACCOUNT_LENGTH || !StringUtils.isNumeric(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号格式错误");
        }
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("password", encryptPassword);
        User user = this.getOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码错误");
        }
        request.getSession().setAttribute(UserConstant.LOGIN_USER, user);
        request.getSession().setMaxInactiveInterval(30 * 60);
        return user;
    }

    @Override
    public UserProfileVO getProfile(Long targetUserId, HttpServletRequest request) {
        User viewer = getLoginUser(request);
        Long resolvedUserId = targetUserId;
        if (resolvedUserId == null) {
            if (viewer == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
            }
            resolvedUserId = viewer.getId();
        }
        User profileUser = this.getById(resolvedUserId);
        if (profileUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        UserProfileStatsVO profileStats = blogMapper.selectUserProfileStats(profileUser.getId());
        long blogCount = profileStats == null || profileStats.getBlogCount() == null ? 0L : profileStats.getBlogCount();
        long likedAndCollectCount = profileStats == null || profileStats.getLikedAndCollectedCount() == null
                ? 0L : profileStats.getLikedAndCollectedCount();
        Long followCountValue = followMapper.selectFolloweeCount(profileUser.getId());
        long followCount = followCountValue == null ? 0L : followCountValue;
        Long fansCountValue = followMapper.selectFollowerCount(profileUser.getId());
        long fansCount = fansCountValue == null ? 0L : fansCountValue;
        Long likesGivenCountValue = thumbMapper.selectThumbCountByUserId(profileUser.getId());
        long likesGivenCount = likesGivenCountValue == null ? 0L : likesGivenCountValue;
        Long favoritesCountValue = favoriteMapper.selectFavoriteCountByUserId(profileUser.getId());
        long favoritesCount = favoritesCountValue == null ? 0L : favoritesCountValue;

        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(profileUser.getId());
        vo.setUsername(profileUser.getUsername());
        vo.setSelf(viewer != null && viewer.getId().equals(profileUser.getId()));
        vo.setFollowing(viewer != null && !vo.isSelf() && isViewerFollowing(viewer.getId(), profileUser.getId()));
        vo.setAvatarUrl(StringUtils.trimToNull(profileUser.getAvatarUrl()));
        vo.setAvatarText(generateAvatarText(profileUser));
        vo.setAvatarBg(pickAvatarBg());
        vo.setBio(StringUtils.defaultIfBlank(profileUser.getBio(), vo.isSelf() ? "还没有填写个人简历" : "TA 还没有填写个人简历"));
        vo.setAge(profileUser.getAge());
        vo.setFollowCount(followCount);
        vo.setFansCount(fansCount);
        vo.setLikedAndCollectedCount(likedAndCollectCount);
        vo.setBlogCount(blogCount);
        vo.setLikesGivenCount(likesGivenCount);
        vo.setNotesCount(favoritesCount);
        return vo;
    }

    @Override
    public void updateMyProfile(User loginUser, UserProfileUpdateRequest request) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "请先登录");
        }
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        if (request.getAge() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "年龄不能为空");
        }
        int age = request.getAge();
        if (age < MIN_AGE || age > MAX_AGE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "年龄需在 1-150 之间");
        }
        String bio = StringUtils.trimToEmpty(request.getBio());
        if (bio.length() > MAX_BIO_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "个人简历不能超过 200 个字符");
        }
        String avatarUrl = StringUtils.trimToNull(request.getAvatarUrl());
        validateAvatarUrl(avatarUrl);

        User update = new User();
        update.setId(loginUser.getId());
        update.setAge(age);
        update.setBio(bio);
        update.setAvatarUrl(avatarUrl);
        boolean updated = this.updateById(update);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存个人资料失败");
        }
        loginUser.setAge(age);
        loginUser.setBio(bio);
        loginUser.setAvatarUrl(avatarUrl);
    }

    private boolean isViewerFollowing(Long viewerId, Long targetUserId) {
        Long count = followMapper.selectCount(new LambdaQueryWrapper<com.example.dianzan.model.entity.Follow>()
                .eq(com.example.dianzan.model.entity.Follow::getFollowerId, viewerId)
                .eq(com.example.dianzan.model.entity.Follow::getFolloweeId, targetUserId));
        return count != null && count > 0;
    }

    private static String generateAvatarText(User user) {
        String base = StringUtils.defaultIfBlank(user.getUsername(), user.getUserAccount());
        base = StringUtils.defaultIfBlank(base, "U");
        return base.substring(0, 1).toUpperCase();
    }

    private static String pickAvatarBg() {
        return DEFAULT_AVATAR_BG;
    }

    private static void validateAvatarUrl(String avatarUrl) {
        if (avatarUrl == null) {
            return;
        }
        if (avatarUrl.length() > MAX_AVATAR_URL_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像地址过长");
        }
        boolean validProtocol = StringUtils.startsWithIgnoreCase(avatarUrl, "http://")
                || StringUtils.startsWithIgnoreCase(avatarUrl, "https://");
        if (!validProtocol) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "头像地址格式不合法");
        }
    }
}
