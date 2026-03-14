package com.example.dianzan.controller;

import com.example.dianzan.common.BaseResponse;
import com.example.dianzan.common.ResultUtils;
import com.example.dianzan.model.vo.BlogVO;
import com.example.dianzan.service.impl.BlogSearchService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("blog")
public class BlogSearchController {

    @Resource
    private BlogSearchService blogSearchService;

    @GetMapping("/search")
    public BaseResponse<List<BlogVO>> search(@RequestParam String keyword, HttpServletRequest request) throws Exception {
        return ResultUtils.success(blogSearchService.search(keyword, request));
    }
}
