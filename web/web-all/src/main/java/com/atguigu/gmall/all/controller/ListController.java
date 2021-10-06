package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ListController {
    @Autowired
    private ListFeignClient listFeignClient;
    // http://list.gmall.com/list.html?category3Id=61
    // http://list.gmall.com/list.html?category3Id=61&trademark=245:%E7%9B%96%E4%BA%9A1&order=

    // http://list.gmall.com/list.html {
    //  "keyword": "手机",
    //  "trademark": "2:华为"
    //}
    /**
     * 列表搜索
     * @param searchParam
     * @return
     */
    @GetMapping("list.html")
    public String search(SearchParam searchParam, Model model){
        Result result = listFeignClient.list(searchParam);
        // 存储数据
        model.addAllAttributes((Map<String, ?>)result.getData());

        // 有关于排序
        // order=2:asc  | order=2:desc
        Map<String,Object> orderMap = dealOrder(searchParam.getOrder());
        model.addAttribute("orderMap",orderMap);
        // 平台属性：
        //  props=23:4G:运行内存
        List<Map<String, String>> propsParamList = makeProps(searchParam.getProps());

        model.addAttribute("propsParamList",propsParamList);

        // 品牌：华为
        // trademark=2:华为
        String trademarkParam=makeTrademark(searchParam.getTrademark());
        //存储参数
        model.addAttribute("trademarkParam",trademarkParam);

        //记录拼接url
        String urlParam = makeUrlParam(searchParam);
        model.addAttribute("urlParam",urlParam);
        return "list/index";
    }


    //排序
    //order=  2:asc  | order=  2:desc
    private Map<String,Object> dealOrder(String order){
        HashMap<String, Object> map = new HashMap<>();
        // 判断
        if (!StringUtils.isEmpty(order)){
            // 分割字符串   2:asc
            String[] split = order.split(":");
            if (split!=null&& split.length==2){
                // 设置数据
                map.put("type",split[0]);
                map.put("sort",split[1]);
            }else {
                // order=1:
                map.put("type","1");
                map.put("sort","desc");
            }
        }else {
            // order=
            map.put("type","1");
            map.put("sort","desc");
        }
        return  map;
    }

    // 获取品牌回显
    private String makeTrademark(String trademark) {
        // trademark=2:华为
        if (!StringUtils.isEmpty(trademark)){
            //进行分割
            String[] split = trademark.split(":");
            //判断格式
            if (split!=null&&split.length==2){
                return "品牌："+split[1];
            }
        }
        return null;
    }

    //处理平台属性的回显
    private List<Map<String,String>> makeProps(String[] props){
        ArrayList<Map<String, String>> list  = new ArrayList<>();
        // 判断当前传入的数据是否为空
        if (props!=null&& props.length>0){
            //循环
            for (String prop : props) {
                String[] split = prop.split(":");
                if (split !=null && split.length == 3){
                    // props=23:4G:运行内存
                    HashMap<String, String> map = new HashMap<>();
                    //获取里面的数据
                    map.put("attrId",split[0]);
                    map.put("attrValue",split[1]);
                    map.put("attrValue",split[2]);
                }
            }
        }
        return list;
    }

    // 有关品牌的面包屑
    // 拼接查询条件? searchParam 这个参数记录着用户选择的哪个查询条件！
    //my:${urlParam}前端超链接需要取值，按照检索条件跳转路径
    private String makeUrlParam(SearchParam searchParam) {
        StringBuilder urlParam = new StringBuilder();
        // 做个条件判断
        // 说明用户首先按照一级分类Id 进行查询的！
        // http://list.gmall.com/list.html?category1Id=61
        if(!StringUtils.isEmpty(searchParam.getCategory1Id())){
           urlParam.append("category1Id=").append(searchParam.getCategory1Id());
        }

        if (!StringUtils.isEmpty(searchParam.getCategory2Id())){
            urlParam.append("category2Id=").append(searchParam.getCategory2Id());
        }

        if (!StringUtils.isEmpty(searchParam.getCategory3Id())){
            urlParam.append("category3Id=").append(searchParam.getCategory3Id());
        }
        // http://list.gmall.com/list.html?keyword=手机
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            urlParam.append("eyword=").append(searchParam.getKeyword());
        }
        // 品牌
        // http://list.gmall.com/list.html?keyword=手机&trademark=2:华为
        if (!StringUtils.isEmpty(searchParam.getTrademark())){
            urlParam.append("&trademark=").append(searchParam.getTrademark());
        }
        // 平台属性值
        // http://list.gmall.com/list.html?keyword=手机&trademark=2:华为&props=1:2800-4499:价格&props=2:6.95英寸及以上:屏幕尺寸
        String[] props = searchParam.getProps();
        if (props!=null && props.length>0){
            for (String prop : props) {
                urlParam.append("&trademark=").append(prop);
            }
        }
        return "list.html?"+urlParam.toString();
    }
}
