package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * 在本项目中web-all是一个管理前端页面的微服务，把从其他微服务得到的数据提供给前端，然后渲染页面
 * 20110131
 */
@Controller
public class IndexController {
    @Autowired
    private ProductFeignClient productFeignClient;

    @GetMapping({"/","index.html"})
    public String index(HttpServletRequest request){
        // 获取首页分类数据
        Result result  = productFeignClient.getBaseCategoryList();
        request.setAttribute("list", result.getData());
        return "index/index";
    }
}
