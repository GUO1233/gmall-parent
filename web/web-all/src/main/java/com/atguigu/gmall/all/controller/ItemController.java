package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.client.ItemFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Controller
public class ItemController {

    @Autowired
    private ItemFeignClient itemFeignClient;
    // http://item.gmall.com/28.html
    //http://item.gmall.com/ 显示访问网关默认80端口 host文件做了域名映射是  网关路由到web-all服务 然后找/28.html控制器
    //192.168.200.1 file.service.com www.gmall.com item.gmall.com order.gmall.com payment.gmall.com
    // 定义映射器
    @RequestMapping("{skuId}.html")
    public String item(@PathVariable Long skuId, HttpServletRequest request, Model model){
        // 获取到当前的skuId
        System.out.println("skuId:\t"+skuId);
        // 获取数据结果
        Result<Map> result = itemFeignClient.getItem(skuId);
        // 保存数据结果，给页面使用！ 页面保存map 即可！
        model.addAllAttributes(result.getData());

        return "item/index";
    }


}
