
use dianzan;
create table blog
(
    id         bigint auto_increment
        primary key,
    userId     bigint                             not null,
    title      varchar(512)                       null comment '标题',
    coverImg   varchar(1024)                      null comment '封面',
    content    text                               not null comment '内容',
    thumbCount int      default 0                 not null comment '点赞数',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
);

create index idx_userId
    on blog (userId);

create table comment
(
    id            bigint auto_increment comment '评论主键ID'
        primary key,
    blogId        bigint                             not null comment '所属博客ID',
    userId        bigint                             not null comment '评论发表人的用户ID',
    content       varchar(1024)                      not null comment '评论内容',
    rootId        bigint   default 0                 null comment '根评论ID。如果是顶级评论则为0；如果是回复，则为顶级评论的ID',
    parentId      bigint   default 0                 null comment '直接回复的那条评论的ID',
    replyToUserId bigint   default 0                 null comment '被回复人的用户ID',
    thumbCount    int      default 0                 null comment '评论的点赞数',
    createTime    datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '文章评论表';

create index idx_blogId
    on comment (blogId);

create index idx_rootId
    on comment (rootId);

create table favorite
(
    id         bigint auto_increment
        primary key,
    userId     bigint                             not null,
    blogId     bigint                             not null,
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint idx_favorite_user_blog
        unique (userId, blogId)
);

create index idx_favorite_blogId
    on favorite (blogId);

create table feedback
(
    id               bigint auto_increment
        primary key,
    userId           bigint                             null comment '提交反馈的用户，匿名可为空',
    usernameSnapshot varchar(128)                       null comment '提交时的用户名快照',
    contact          varchar(100)                       null comment '联系方式（可选）',
    content          varchar(500)                       not null comment '反馈内容',
    pagePath         varchar(255)                       null comment '反馈来源页面',
    status           tinyint  default 0                 not null comment '0待处理 1已处理',
    createTime       datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime       datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '用户反馈表';

create index idx_feedback_status
    on feedback (status);

create index idx_feedback_userId
    on feedback (userId);

create table follow
(
    id         bigint auto_increment
        primary key,
    followerId bigint                             not null comment '关注者（粉丝）',
    followeeId bigint                             not null comment '被关注者',
    createTime datetime default CURRENT_TIMESTAMP not null comment '关注时间',
    constraint idx_follower_followee
        unique (followerId, followeeId)
);

create index idx_followeeId
    on follow (followeeId);

create table notification
(
    id         bigint auto_increment
        primary key,
    userId     bigint                             not null comment '接收通知的用户',
    fromUserId bigint                             not null comment '触发通知的用户',
    blogId     bigint   default 0                 not null comment '关联博客ID，关注通知为0',
    type       tinyint                            not null comment '1评论 2点赞 3关注',
    isRead     tinyint  default 0                 not null,
    createTime datetime default CURRENT_TIMESTAMP not null
);

create table thumb
(
    id         bigint auto_increment
        primary key,
    userId     bigint                             not null,
    blogId     bigint                             not null,
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint idx_userId_blogId
        unique (userId, blogId)
);

create table user
(
    id          bigint auto_increment
        primary key,
    userAccount varchar(11)   not null comment '登录账号，11 位纯数字',
    username    varchar(128)  not null comment '用户昵称',
    password    varchar(128)  not null comment '密码（建议存放加盐 MD5）',
    role        int default 0 not null comment '0 普通用户 1 管理员',
    age         int           null comment '年龄',
    avatarUrl   varchar(1024) null comment '头像地址',
    bio         varchar(200)  null comment '个人简介',
    constraint idx_userAccount
        unique (userAccount)
)
    comment '用户表';


