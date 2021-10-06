package com.atguigu.gmall.item.service;

import java.util.Map;

public interface ItemService {
    /**
     * 获取sku详情信息
     * @param skuId
     * @return
     * my：为什莫要返回Map集合,MAP集合要 分装下面的a,b,c,d,e做汇总
     * 	a.	Sku基本信息
     * 		skuName, skuDefaultImage,weight
     * 	b.	Sku图片信息
     * 		skuImage
     * 	c.	Sku分类信息
     * 		三个分类表
     * 	d.	Sku销售属性相关信息
     * 		1.	商品的销售属性回显
     * 		2.	商品的销售属性值锁定
     * 		3.	点击销售属性值的切换
     *
     * 	e.	Sku价格信息
     * 		后续需要使用到商品的价格校验操作！
     * 		select price from sku_info where id = ?
     * 	为什么要 传入skuId因为这几项信息都与sku有关
     */
    Map<String, Object> getBySkuId(Long skuId);

}
