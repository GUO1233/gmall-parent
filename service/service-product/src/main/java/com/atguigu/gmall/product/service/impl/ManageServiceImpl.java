package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ManageServiceImpl implements ManageService {
    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;

    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;

    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;

    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    private SpuInfoMapper spuInfoMapper;
//==========================================4day=========================
    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;

    @Autowired
    private SpuImageMapper spuImageMapper;

    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;

    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;

    @Autowired
    private SkuInfoMapper skuInfoMapper;

    @Autowired
    private SkuImageMapper skuImageMapper;

    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;

    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;

    //====================================================
    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;

    //==============================================
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    //====================================
    @Autowired
    private BaseTrademarkMapper baseTrademarkMapper;

    //====================消息通知=======================
    @Autowired
    private RabbitService rabbitService;


    @Override
    public List<BaseCategory1> getCategory1() {
        return baseCategory1Mapper.selectList(null);
    }



    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        QueryWrapper<BaseCategory2> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category1_id",category1Id);
        return baseCategory2Mapper.selectList(queryWrapper);
    }

    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        QueryWrapper<BaseCategory3> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category2_id",category2Id);
        return baseCategory3Mapper.selectList(queryWrapper);
    }

    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, long category2Id, long category3Id) {
        // 调用mapper：
        return baseAttrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);

    }
//==============================平台属性========================================
    @Override
    @Transactional
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        // 什么情况下 是添加，什么情况下是更新，修改 根据baseAttrInfo 的Id
        // baseAttrInfo
        if (baseAttrInfo.getId() != null) {
            // 修改数据
            baseAttrInfoMapper.updateById(baseAttrInfo);
        } else {
            // 新增
            // baseAttrInfo 插入数据
            baseAttrInfoMapper.insert(baseAttrInfo);
        }

        // baseAttrValue 平台属性值
        // 修改：通过先删除{baseAttrValue}，在新增的方式！
        // 删除条件：baseAttrValue.attrId = baseAttrInfo.id
        QueryWrapper queryWrapper = new QueryWrapper<BaseAttrValue>();
        queryWrapper.eq("attr_id", baseAttrInfo.getId());
        baseAttrValueMapper.delete(queryWrapper);

        // 获取页面传递过来的所有平台属性值数据
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        if (attrValueList != null && attrValueList.size() > 0) {
            // 循环遍历
            for (BaseAttrValue baseAttrValue : attrValueList) {
                // 获取平台属性Id 给attrId
                baseAttrValue.setAttrId(baseAttrInfo.getId()); // my:前台只会传来一个valueName属性
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }

    }

    @Override
    public BaseAttrInfo getAttrInfo(Long attrId) {
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);
        // 查询到最新的平台属性值集合数据放入平台属性中！
        baseAttrInfo.setAttrValueList(getAttrValueList(attrId));
        return baseAttrInfo;
    }



    /**
     * 根据属性id获取属性值
     * @param attrId
     * @return
     */

    private List<BaseAttrValue> getAttrValueList(Long attrId) {
        // select * from baseAttrValue where attrId = ?
        QueryWrapper queryWrapper = new QueryWrapper<BaseAttrValue>();
        queryWrapper.eq("attr_id", attrId);
        List<BaseAttrValue> baseAttrValueList = baseAttrValueMapper.selectList(queryWrapper);
        return baseAttrValueList;
    }

    @Override
    public IPage<SpuInfo> getSpuInfoPage(Page<SpuInfo> pageParam, SpuInfo spuInfo) {
        QueryWrapper<SpuInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category3_id",spuInfo.getCategory3Id());
        queryWrapper.orderByDesc("id");
        return spuInfoMapper.selectPage(pageParam,queryWrapper);
    }
//=====================================4day==========================================
    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        List<BaseSaleAttr> baseSaleAttrs = baseSaleAttrMapper.selectList(null);
        return baseSaleAttrs;
    }

    @Override
    @Transactional
    public void saveSpuInfo(SpuInfo spuInfo) {
        //spuINfo 商品表
        spuInfoMapper.insert(spuInfo);
        //spuImage商品图片表
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        if(spuImageList!=null&&spuImageList.size()>0){
            for (SpuImage spuImage : spuImageList) {
                Long spuInfoId = spuInfo.getId();
                spuImage.setSpuId(spuInfoId);
                spuImageMapper.insert(spuImage);
            }
        }
//        spuSaleAttr 销售属性表
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        if(spuSaleAttrList!=null&&spuSaleAttrList.size()>0){
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);
                //        spuSaleAttrValue 销售属性值表
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if(spuSaleAttrValueList!=null&&spuSaleAttrValueList.size()>0){
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }

            }
        }



    }

    @Override
    public List<SpuImage> getSpuImagelist(Long spuId) {
        //select * from spu_image where spu_id = spuId;
        QueryWrapper<SpuImage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("spu_id",spuId);
        return spuImageMapper.selectList(queryWrapper);
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
    }

    @Override
    @Transactional
    public void saveSkuInfo(SkuInfo skuInfo) {
        //操作skuinfo这个表
        skuInfoMapper.insert(skuInfo);
        //操作sku_image这个表
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if(skuImageList != null && skuImageList.size() > 0){
            for (SkuImage skuImage : skuImageList) {
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }
        //操作sku_sale_attr_value、sku_attr_value
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if (!CollectionUtils.isEmpty(skuSaleAttrValueList)) {
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }

        //操作sku_attr_value
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if (!CollectionUtils.isEmpty(skuAttrValueList)) {
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }

        //发送消息，通知es 进行上架！
        // 根据商品的Id skuId 进行商品的上架    my: 保存sku商品自动上架
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,MqConst.ROUTING_GOODS_UPPER,skuInfo.getId());

    }

    @Override
    public IPage<SkuInfo> getPage(Page<SkuInfo> pageParam) {
        QueryWrapper<SkuInfo> queryWrapper  = new QueryWrapper<>();
        queryWrapper.orderByDesc("id");
        return skuInfoMapper.selectPage(pageParam,queryWrapper);
    }

    @Override
    public void onSale(Long skuId) {
        // update sku_info set is_sale=1 where id = skuId;
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(1);
        skuInfoMapper.updateById(skuInfo);

        //商品上架发送消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,MqConst.ROUTING_GOODS_UPPER,skuId);
    }

    @Override
    public void cancelSale(Long skuId) {
        // update sku_info set is_sale=0 where id = skuId;
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(0);
        skuInfoMapper.updateById(skuInfo);

        //商品下架发送消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_GOODS,MqConst.ROUTING_GOODS_LOWER,skuId);
    }
//====================================商品详情页渲染====================================================
    @Override
    @GmallCache(prefix = "getSkuInfo:")
    public SkuInfo getSkuInfo(Long skuId) {
//        return getSkuInfoRedisSet(skuId);
//        return getSkuInfoRedisson(skuId);
        return getSkuInfoDB(skuId);
    }

    private SkuInfo getSkuInfoRedisSet(Long skuId) {
        // 声明对象
        SkuInfo skuInfo = null;
        try {
            // 先获取缓存中的数据！
            // hset(key,field,value) field = 对象的数据。
            // redisTemplate.opsForHash() 没有毛病！但是，我不用！
            // String set(key) get (key)
            // 定义存储数据的key 是什么?
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 应该序列化，反序列化。RedisConfig 中已经配置好了！
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            // 判断
            if (skuInfo==null){
                // 说明缓存中没有数据,从数据库中获取数据，并放入缓存！
                // 防止缓存击穿！
                // 声明锁的key = sku:skuId:lock
                String lockkey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKULOCK_SUFFIX;
                // 声明一个UUID
                String uuid = UUID.randomUUID().toString();
                // 上锁
                Boolean flag = redisTemplate.opsForValue().setIfAbsent(lockkey, uuid,RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
                // 表示上锁成功！
                if (flag){
                    // 获取数据库中的数据，防止缓存穿透！数据库中根本没有这个数据！
                    // 100009082466.html
                    skuInfo = getSkuInfoDB(skuId);
                    if (skuInfo==null){
                        SkuInfo skuInfo1 = new SkuInfo();
                        // 设置过期时间并返回
                        redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        return skuInfo1;
                    }
                    // 如果不为空，则直接放入缓存！
                    redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);

                    // 声明lua 脚本
                    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                    // java 客户端如何调用，并执行！
                    // RedisScript
                    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script);
                    // 设置返回类型 , 默认是Object ，设置成Long 数据类型是因为：lua 脚本。
                    redisScript.setResultType(Long.class);
                    // redis 中，执行lur 脚本。
                    redisTemplate.execute(redisScript, Arrays.asList(lockkey),uuid);
                    // 返回数据！
                    return skuInfo;
                }else{
                    // 没有获取到锁！等待自旋
                    try {
                        Thread.sleep(1000);
                        return getSkuInfo(skuId);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }else {
                // 说明缓存有数据
                return skuInfo;
            }
        } catch (Exception e) {
            // log 日志，记录。最好附加发送信息给运维。
            e.printStackTrace();
        }
        // 返回对象
        return getSkuInfoDB(skuId);
    }

    private SkuInfo getSkuInfoRedisson(Long skuId) {
        // 声明对象
        SkuInfo skuInfo = null;
        try {
            // 先获取缓存中的数据！
            // hset(key,field,value) field = 对象的数据。
            // redisTemplate.opsForHash() 没有毛病！但是，我不用！
            // String set(key) get (key)
            // 定义存储数据的key 是什么?
            String skuKey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKUKEY_SUFFIX;
            // 应该序列化，反序列化。RedisConfig 中已经配置好了！
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);//my:根据商品的skuId制作key上锁，实现对每个商品的枷锁
            // 判断
            if (skuInfo==null){
                // 说明缓存中没有数据,从数据库中获取数据，并放入缓存！
                // 防止缓存击穿！
                // 声明锁的key = sku:skuId:lock
                String lockkey = RedisConst.SKUKEY_PREFIX+skuId+RedisConst.SKULOCK_SUFFIX;
                RLock lock = redissonClient.getLock(lockkey);
                // 上锁
                // 方式一：lock.lock(); 默认30秒
                // 方式二：lock.lock(10,TimeUnit.SECONDS);
                // 获取数据库中的数据，防止缓存穿透！数据库中根本没有这个数据！
                // 100009082466.html
                boolean res = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
                // 表示上锁成功！
                if (res){
                    // 获取数据库中的数据，防止缓存穿透！数据库中根本没有这个数据！
                    // 100009082466.html
                    skuInfo = getSkuInfoDB(skuId);    //my:如果不在查询数据库之前就加锁，可能高并发访问数据库，给服务器造成压力
                    if (skuInfo==null){
                        SkuInfo skuInfo1 = new SkuInfo();
                        // 设置过期时间并返回
                        redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                        return skuInfo1;
                    }
                    // 如果不为空，则直接放入缓存！
                    redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);

                    // 返回数据！
                    return skuInfo;
                }else{
                    // 没有获取到锁！等待自旋
                    try {
                        Thread.sleep(1000);
                        return getSkuInfo(skuId);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }else {
                // 说明缓存有数据
                return skuInfo;
            }
        } catch (Exception e) {
            // log 日志，记录。最好附加发送信息给运维。
            e.printStackTrace();
        }
        // 返回对象
        return getSkuInfoDB(skuId);
    }

    private SkuInfo getSkuInfoDB(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        // 根据skuId 查询图片列表集合
        QueryWrapper<SkuImage> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("sku_id",skuId);
        List<SkuImage> skuImageList = skuImageMapper.selectList(queryWrapper);

        skuInfo.setSkuImageList(skuImageList);
        return skuInfo;
    }


    @Override
    // 利用aop 实现
    @GmallCache(prefix = "getCategoryView:")
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    @Override
    // 利用aop 实现
    @GmallCache(prefix = "getCategoryView:")
    public BigDecimal getSkuPrice(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if(null != skuInfo){
            return skuInfo.getPrice();
    }
        return new BigDecimal("0");
    }

    @Override
    @GmallCache(prefix = "getSpuSaleAttrListCheckBySku:")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId, spuId);
    }

    @Override
    @GmallCache(prefix = "getSkuValueIdsMap:")
    public Map getSkuValueIdsMap(Long spuId) {
        Map<Object, Object> hashMap = new HashMap<>();
        // key = 125|123 ,value = 37
        
        List<Map> mapList = skuSaleAttrValueMapper.selectSaleAttrValuesBySpu(spuId);
        // 判断集合不为空
        if(!CollectionUtils.isEmpty(mapList)){
            // key = values_ids ,value = 125|123   key = sku_id ,value = 37
            for (Map skuMap : mapList) {
                hashMap.put(skuMap.get("values_ids"),skuMap.get("sku_id"));
            }
        }

        return hashMap;
    }

    /**
     * my:
     * 每个单个分类比如一个二级分类包装一个JSONObject，多个同层次的二级分类封装为一个LIST集合，
     * 然后放入对应的单个一级分类的JSONObject
     * 多个一级分类的JSONObject，又封装为list集合
     * @return
     */
    @Override
    @GmallCache(prefix = "category")
    public List<JSONObject> getBaseCategoryList() {
        //声明几个json 集合
        ArrayList<JSONObject> list  = new ArrayList<>();

        //声明所有的分类数据的集合
        List<BaseCategoryView> baseCategoryViews = baseCategoryViewMapper.selectList(null);
        // 循环上面的集合并安一级分类Id 进行分组
        /**
         * 对象.普通方法();
         *  * 			条件：
         *  * 				a.必须满足Lambda表达式条件(只有一个抽象方法)
         *  * 				b.实现的方法体中，只能有一行代码
         *  * 				c.这行代码必须是方法的调用
         *  * 				d.抽象方法的第一个参数是调用方法的调用者，抽象方法的后续参数和调用方法的参数一致
         * 			Lambda    (t,u)->t.contains(u);
         * 			方法引用     类名::普通方法        t的类型::方法名;      String::contains;
         *
         * 	collect(Collector c)   将流转换为其他形式
         *  *  		Collector
         *  *  			Collectors.toList()  设置讲数据存储到list集合中
         *  *  			Collectors.toSet()  设置讲数据存储到set集合中
         *  *  			Collectors.toMap()  设置讲数据存储到map集合中
         */
        Map<Long, List<BaseCategoryView>> category1Map = baseCategoryViews.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id));
        int index=1;
        // 获取一级分类下所有数据
        for (Map.Entry<Long, List<BaseCategoryView>> entry1 : category1Map.entrySet()) {
            // 获取一级分类Id
            Long category1Id   = entry1.getKey();
            // 获取一级分类下面的所有集合
            List<BaseCategoryView> category2List1   = entry1.getValue();//my:category2List1已经是单个根据一级分类的集合，里面包括多个二级分类
            JSONObject category1  = new JSONObject();
            //封装一级分类数据index categoryId categoryName
            category1.put("index", index);
            category1.put("categoryId",category1Id);
            // 一级分类名称
            category1.put("categoryName",category2List1.get(0).getCategory1Name());

            // 变量迭代
            index++;
            // 循环获取二级分类数据
            Map<Long, List<BaseCategoryView>> category2Map  = category2List1.stream().collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id));
            // 声明二级分类对象集合
            List<JSONObject> category2Child = new ArrayList<>();
            for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
                // 获取二级分类Id
                Long category2Id  = entry2.getKey();
                // 获取二级分类下的所有集合
                List<BaseCategoryView> category3List  = entry2.getValue();
                // 声明二级分类对象
                JSONObject category2 = new JSONObject();

                category2.put("categoryId",category2Id);
                category2.put("categoryName",category3List.get(0).getCategory2Name());
                // 添加到二级分类集合
                category2Child.add(category2);

                List<JSONObject> category3Child = new ArrayList<>();
                category3List.stream().forEach(category3View -> {
                    JSONObject category3 = new JSONObject();
                    category3.put("categoryId",category3View.getCategory3Id());
                    category3.put("categoryName",category3View.getCategory3Name());

                    category3Child.add(category3);
                });
                // 将三级数据放入二级里面
                category2.put("categoryChild",category3Child);//把整个三级分类的集合放入二级分类的JSONObject里面


            }
            // 将二级数据放入一级里面
            category1.put("categoryChild",category2Child);
            list.add(category1);

        }
        return list;
    }
//=========================商品的上架下架=============================
    //com.atguigu.gmall.list.service.impl.SearchServiceImpl
    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {
        return baseTrademarkMapper.selectById(tmId);
    }

    @Override
    public List<BaseAttrInfo> getAttrList(Long skuId) {
        return baseAttrInfoMapper.selectBaseAttrInfoListBySkuId(skuId);
    }
}
