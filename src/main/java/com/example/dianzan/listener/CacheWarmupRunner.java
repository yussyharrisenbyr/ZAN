package com.example.dianzan.listener;

import com.example.dianzan.controller.BlogController;
import com.example.dianzan.controller.MainController;
import com.example.dianzan.model.vo.HotBlogVO;
import com.example.dianzan.service.BlogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动后的轻量缓存预热。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmupRunner implements ApplicationRunner {

    private static final int HOME_FIRST_PAGE_SIZE = 15;
    private static final int HOT_DETAIL_WARMUP_LIMIT = 5;

    private final BlogController blogController;
    private final MainController mainController;
    private final BlogService blogService;

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        try {
            mainController.warmupOverviewCache();
            blogController.warmupFirstPageCache(HOME_FIRST_PAGE_SIZE);

            List<HotBlogVO> hotBlogs = blogController.warmupHotListCache(HOT_DETAIL_WARMUP_LIMIT);
            int warmed = 0;
            for (HotBlogVO hotBlog : hotBlogs) {
                if (hotBlog == null || hotBlog.getBlogId() == null) {
                    continue;
                }
                blogService.warmupAnonymousBlogDetailCache(hotBlog.getBlogId());
                warmed++;
            }
            log.info("缓存预热完成：站点概览=1 份，首页首屏={} 条，热门列表={} 条，热门详情={} 条，耗时={}ms",
                    HOME_FIRST_PAGE_SIZE, hotBlogs.size(), warmed, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("缓存预热过程中发生异常，跳过本轮预热", e);
        }
    }
}

