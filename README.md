# ZAN（点赞系统）

一个基于 Spring Boot 的博客点赞与互动平台，支持点赞、收藏、关注、评论、消息通知、全文搜索等功能。

## 技术栈

| 组件 | 版本 / 说明 |
|------|------------|
| Java | 21 |
| Spring Boot | 3.5.x |
| 数据库 | MySQL / TiDB |
| 缓存 | Redis（Jedis 连接池）+ Caffeine 本地缓存 |
| 消息队列 | Apache Pulsar |
| 搜索引擎 | Elasticsearch 7.x |
| ORM | MyBatis-Plus |
| 对象存储 | 阿里云 OSS |
| 接口文档 | Knife4j（OpenAPI 3） |
| Session | Spring Session（Redis 存储）|
| 容器化 | Docker Compose |

---

## 目录结构

```
ZAN/
├── docker-compose.yml              # 本地开发环境（ES、Kibana、TiDB、Pulsar、Redis）
├── pom.xml                         # Maven 依赖配置
├── sql/
│   └── create.sql                  # 数据库建表 SQL
└── src/
    ├── main/
    │   ├── java/com/example/dianzan/
    │   │   ├── DianzanApplication.java          # 启动类
    │   │   ├── common/                          # 公共响应体、分页请求、工具方法
    │   │   │   ├── BaseResponse.java
    │   │   │   ├── PageRequest.java
    │   │   │   └── ResultUtils.java
    │   │   ├── config/                          # 各组件配置
    │   │   │   ├── CorsConfig.java              # 跨域配置
    │   │   │   ├── JacksonConfig.java           # JSON 序列化配置
    │   │   │   ├── OssClientConfig.java         # 阿里云 OSS 客户端
    │   │   │   ├── OssProperties.java           # OSS 配置属性
    │   │   │   ├── RedisConfig.java             # Redis 序列化配置
    │   │   │   └── ThumbConsumerConfig.java     # Pulsar 消费者配置
    │   │   ├── constant/                        # 常量定义
    │   │   │   ├── RedisLuaScriptConstant.java  # Redis Lua 脚本
    │   │   │   ├── ThumbConstant.java           # 点赞相关常量
    │   │   │   └── UserConstant.java            # 用户相关常量
    │   │   ├── controller/                      # REST 接口层
    │   │   │   ├── BlogController.java          # 博客 CRUD
    │   │   │   ├── BlogSearchController.java    # 博客全文搜索
    │   │   │   ├── CommentController.java       # 评论
    │   │   │   ├── FavoriteController.java      # 收藏
    │   │   │   ├── FeedbackController.java      # 意见反馈
    │   │   │   ├── FollowController.java        # 关注 / 取关
    │   │   │   ├── MainController.java          # 首页 & 站点概览
    │   │   │   ├── NotificationController.java  # 消息通知
    │   │   │   ├── OssController.java           # 文件上传（OSS 预签名）
    │   │   │   ├── ThumbController.java         # 点赞 / 取消点赞
    │   │   │   └── UserController.java          # 用户注册、登录、资料
    │   │   ├── example/                         # 示例 / 工具类（非生产代码）
    │   │   │   ├── BlogThumbSyncJob.java
    │   │   │   ├── GetSignUrl.java
    │   │   │   └── SignUrlUpload.java
    │   │   ├── exception/                       # 异常体系
    │   │   │   ├── BusinessException.java
    │   │   │   ├── ErrorCode.java
    │   │   │   ├── GlobalExceptionHandler.java
    │   │   │   └── ThrowUtils.java
    │   │   ├── interceptor/                     # Spring MVC 拦截器
    │   │   │   ├── LoginInterceptor.java        # 登录校验
    │   │   │   ├── PerformanceLogInterceptor.java # 接口耗时日志
    │   │   │   └── RefreshInterceptor.java      # Session 刷新
    │   │   ├── job/                             # 定时任务
    │   │   │   ├── SyncThumb2DBJob.java         # 点赞数据定时落库
    │   │   │   ├── SyncThumb2DBCompensatoryJob.java # 补偿任务
    │   │   │   └── ThumbReconcileJob.java       # 点赞数据对账
    │   │   ├── listener/                        # 应用启动监听 & 消息消费
    │   │   │   ├── BlogImageSchemaInitializer.java
    │   │   │   ├── CacheWarmupRunner.java       # 缓存预热
    │   │   │   ├── FeedbackSchemaInitializer.java
    │   │   │   └── thumb/
    │   │   │       ├── ThumbConsumer.java       # Pulsar 点赞消息消费者
    │   │   │       └── msg/
    │   │   │           └── ThumbEvent.java      # 点赞事件消息体
    │   │   ├── manager/
    │   │   │   └── cache/                       # 热点 Key 识别（HeavyKeeper TopK）
    │   │   │       ├── CacheManager.java
    │   │   │       ├── HeavyKeeper.java
    │   │   │       ├── Item.java
    │   │   │       └── TopK.java
    │   │   ├── mapper/                          # MyBatis-Plus Mapper 接口
    │   │   │   ├── BlogMapper.java
    │   │   │   ├── CommentMapper.java
    │   │   │   ├── FavoriteMapper.java
    │   │   │   ├── FeedbackMapper.java
    │   │   │   ├── FollowMapper.java
    │   │   │   ├── NotificationMapper.java
    │   │   │   ├── ThumbMapper.java
    │   │   │   └── UserMapper.java
    │   │   ├── model/
    │   │   │   ├── dto/                         # 请求 DTO
    │   │   │   │   ├── favorite/DoFavoriteRequest.java
    │   │   │   │   ├── thumb/
    │   │   │   │   │   ├── DoThumbRequest.java
    │   │   │   │   │   └── ThumbRedisData.java
    │   │   │   │   ├── FeedbackSubmitRequest.java
    │   │   │   │   ├── OssPresignRequest.java
    │   │   │   │   ├── UserLoginRequest.java
    │   │   │   │   ├── UserProfileUpdateRequest.java
    │   │   │   │   └── UserRegisterRequest.java
    │   │   │   ├── entity/                      # 数据库实体
    │   │   │   │   ├── Blog.java
    │   │   │   │   ├── BlogDoc.java             # ES 文档实体
    │   │   │   │   ├── Comment.java
    │   │   │   │   ├── Favorite.java
    │   │   │   │   ├── Feedback.java
    │   │   │   │   ├── Follow.java
    │   │   │   │   ├── Notification.java
    │   │   │   │   ├── Thumb.java
    │   │   │   │   └── User.java
    │   │   │   ├── enums/                       # 枚举
    │   │   │   │   ├── LuaStatusEnum.java
    │   │   │   │   └── ThumbTypeEnum.java
    │   │   │   └── vo/                          # 响应 VO
    │   │   │       ├── AuthorStatsVO.java
    │   │   │       ├── BlogPageVO.java
    │   │   │       ├── BlogVO.java
    │   │   │       ├── CommentVO.java
    │   │   │       ├── FavoriteActionResponse.java
    │   │   │       ├── FollowActionVO.java
    │   │   │       ├── FollowPageVO.java
    │   │   │       ├── FollowUserVO.java
    │   │   │       ├── HotBlogVO.java
    │   │   │       ├── NotificationItemVO.java
    │   │   │       ├── NotificationPageVO.java
    │   │   │       ├── NotificationStatsVO.java
    │   │   │       ├── OssPresignVO.java
    │   │   │       ├── SiteOverviewVO.java
    │   │   │       ├── ThumbActionResponse.java
    │   │   │       ├── UserProfileStatsVO.java
    │   │   │       └── UserProfileVO.java
    │   │   ├── repository/
    │   │   │   └── BlogEsRepository.java        # Spring Data ES Repository
    │   │   ├── service/                         # 业务接口
    │   │   │   ├── impl/                        # 业务实现
    │   │   │   │   ├── BlogSearchService.java
    │   │   │   │   ├── BlogServiceImpl.java
    │   │   │   │   ├── CommentServiceImpl.java
    │   │   │   │   ├── FavoriteServiceImpl.java
    │   │   │   │   ├── FeedbackServiceImpl.java
    │   │   │   │   ├── FollowServiceImpl.java
    │   │   │   │   ├── OssUploadServiceImpl.java
    │   │   │   │   ├── ThumbServiceUltimateImpl.java
    │   │   │   │   └── UserServiceImpl.java
    │   │   │   ├── BlogService.java
    │   │   │   ├── CommentService.java
    │   │   │   ├── FavoriteService.java
    │   │   │   ├── FeedbackService.java
    │   │   │   ├── FollowService.java
    │   │   │   ├── OssUploadService.java
    │   │   │   ├── ThumbService.java
    │   │   │   └── UserService.java
    │   │   └── util/                            # 工具类
    │   │       ├── BlogImageUtils.java
    │   │       ├── CacheSourceContext.java
    │   │       └── RedisKeyUtil.java
    │   └── resources/
    │       ├── application.yml                  # 应用配置
    │       ├── logback.xml                      # 日志配置
    │       ├── mapper/                          # MyBatis XML 映射文件
    │       │   ├── BlogMapper.xml
    │       │   ├── CommentMapper.xml
    │       │   ├── FavoriteMapper.xml
    │       │   ├── FollowMapper.xml
    │       │   ├── ThumbMapper.xml
    │       │   └── UserMapper.xml
    │       └── static/                          # 前端静态资源
    │           ├── index.html                   # 首页
    │           ├── blog.html                    # 博客详情页
    │           ├── profile.html                 # 用户主页
    │           ├── publish.html                 # 发布博客
    │           ├── follow-list.html             # 关注列表
    │           ├── notifications.html           # 消息通知页
    │           ├── avatar-render.js
    │           ├── common-navbar.css
    │           ├── oss-upload.js
    │           ├── shell-embed-guard.js
    │           └── site-navigation.js
    └── test/
        └── java/com/example/dianzan/           # 单元测试 & 集成测试
            ├── DianzanApplicationTests.java
            ├── FavoriteWriteAccessTest.java
            ├── FeedbackSubmitAccessTest.java
            ├── FollowServiceImplTest.java
            ├── HotBlogAccessTest.java
            ├── OssUploadAccessTest.java
            ├── ProfileFollowPublicAccessTest.java
            ├── PublicEndpointAccessTest.java
            ├── UserBlogPublicAccessTest.java
            ├── UserFavoritePublicAccessTest.java
            ├── UserProfileUpdateAccessTest.java
            └── UserThumbPublicAccessTest.java
```

---

## 快速启动

### 1. 启动依赖服务（Docker Compose）

```bash
docker-compose up -d
```

> 将在本地启动：TiDB（MySQL 兼容，端口 4000）、Redis（6379）、Elasticsearch（9200）、Kibana（5601）、Apache Pulsar（6650）。

### 2. 初始化数据库

```bash
mysql -h 127.0.0.1 -P 4000 -u root < sql/create.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.yml`，将数据库、Redis、OSS 等连接信息替换为实际环境配置。

### 4. 构建并运行

```bash
mvn spring-boot:run
```

启动后访问：
- 前端首页：[http://localhost:8080](http://localhost:8080)
- 接口文档（Knife4j）：[http://localhost:8080/doc.html](http://localhost:8080/doc.html)

---

## 主要功能模块

| 模块 | 说明 |
|------|------|
| 用户 | 注册、登录、资料修改、头像上传 |
| 博客 | 发布、编辑、删除、分页浏览、全文搜索 |
| 点赞 | 点赞 / 取消点赞，Redis 缓存 + Pulsar 异步落库 + 定时对账 |
| 收藏 | 收藏 / 取消收藏 |
| 评论 | 发表评论、查看评论列表 |
| 关注 | 关注 / 取关用户，查看关注 / 粉丝列表 |
| 通知 | 点赞、评论、关注等行为触发系统通知 |
| 文件上传 | 阿里云 OSS 预签名上传 |
| 热榜 | 基于 HeavyKeeper TopK 算法的热点博客识别 |
