package com.atguigu.gmall.list.service.impl;


import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.sun.org.apache.bcel.internal.generic.IF_ACMPEQ;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.logging.Filter;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private RedisTemplate redisTemplate;
    // 注入操作es 的高级客户端
    @Autowired
    private RestHighLevelClient restHighLevelClient;


    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private GoodsRepository goodsRepository;
        /*
        	1，Sku基本信息（详情业务已封装了接口）skuInfo
            2，Sku分类信息（详情业务已封装了接口）baseCategoryView
            3，Sku对应的平台属性（无）
            4，Sku的品牌信息（无）
            5, 将数据保存到es 中！
            es 保存数据的dsl语句。
            PUT /INDEX/TYPE/1
            {
                JSON 字符串。
            }
            上述应该是固定的dsl 语句写法，不适合我们现在场景！
         */

    /**
     * sku上架 下架 会传入一个skuId
     * my:把数据库中查询得到的数据分装到 实体bean Good中，通过ElasticsearchRepository把一个goods对象
     * 转换为es中的一条文档数据
     * @param skuId
     */
    @Override
    public void upperGoods(Long skuId) {
        Goods goods = new Goods();
        //goods赋值
        SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
        if(skuInfo!=null){
            goods.setId(skuInfo.getId());
            goods.setTitle(skuInfo.getSkuName());
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());
            goods.setPrice(skuInfo.getPrice().doubleValue());
            goods.setCreateTime(new Date());
        }
        //赋值分类信息
        BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
        goods.setCategory1Id(categoryView.getCategory1Id());
        goods.setCategory2Id(categoryView.getCategory2Id());
        goods.setCategory3Id(categoryView.getCategory3Id());
        goods.setCategory1Name(categoryView.getCategory1Name());
        goods.setCategory2Name(categoryView.getCategory2Name());
        goods.setCategory3Name(categoryView.getCategory3Name());

        //Sku的品牌信息（无）
        BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
        goods.setTmId(trademark.getId());
        goods.setTmName(trademark.getTmName());
        goods.setTmLogoUrl(trademark.getLogoUrl());

        // 获取平台属性数据
        List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);

        List<SearchAttr> searchAttrList = attrList.stream().map(baseAttrInfo -> {
            // 声明对象
            SearchAttr searchAttr = new SearchAttr();

            searchAttr.setAttrId(baseAttrInfo.getId());
            searchAttr.setAttrName(baseAttrInfo.getAttrName());
            // 因为当前的平台属性中，对应的属性值集合只有一条数据，所以我们获取的0。
            searchAttr.setAttrValue(baseAttrInfo.getAttrValueList().get(0).getValueName());
            // 返回对象
            return searchAttr;
        }).collect(Collectors.toList());
        goods.setAttrs(searchAttrList);

        /*List<SearchAttr> searchAttrList = new ArrayList<>();

        if (!CollectionUtils.isEmpty(attrList)){
            for (BaseAttrInfo baseAttrInfo : attrList) {
                SearchAttr searchAttr = new SearchAttr();
                searchAttr.setAttrId(baseAttrInfo.getId());
                searchAttr.setAttrName(baseAttrInfo.getAttrName());
                // 赋值属性值名称
                searchAttr.setAttrValue(baseAttrInfo.getAttrValueList().get(0).getValueName());
                searchAttrList.add(searchAttr);
            }
            goods.setAttrs(searchAttrList);
        }*/




        this.goodsRepository.save(goods);
    }

    /**
     * 删除es中的文档数据
     * @param skuId
     */
    @Override
    public void lowerGoods(Long skuId) {
        goodsRepository.deleteById(skuId);
    }
//====================商品的热度排名===================================
    @Override
    public void incrHotScore(Long skuId) {
        // 定义key
        String hotKey = "hotScore";
        //保存数据
        Double hotCount = redisTemplate.opsForZSet().incrementScore(hotKey, "skuId:" + skuId, 1);
        //  定义规则商品被访问10次以后，更新es！
        if (hotCount%10==0){
            Optional<Goods> optional = goodsRepository.findById(skuId);
            // optional 通过skuId 获取到的数据，我们需要在这个数据中获取到Goods
            Goods goods = optional.get();
            // 课件：goods.setHotScore(Math.round(hotCount));
            goods.setHotScore(hotCount.longValue());
            // 将最新的goods 存储到es 中。
            this.goodsRepository.save(goods);

        }
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        // 参考api https://www.elastic.co/guide/en/elasticsearch/client/java-rest/6.8/java-rest-high-search.html
        /*
        1.  创建一个SearchRequest 对象
        2.  准备执行 dsl 语句
        3.  获取到执行结果，将结果封装到 SearchResponseVo 对象中！
         */
        // 手动编写dsl 语句，返回结果searchRequest

        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        //调用高级客户端进行查询
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        //        // 将结果封装到 SearchResponseVo 对象中！
        SearchResponseVo searchResponseVo = this.parseSearchResult(searchResponse);

        //赋值
        searchResponseVo.setPageNo(searchParam.getPageNo());
        searchResponseVo.setPageSize(searchParam.getPageSize());
        // totalPages 总页数
        // 9 ，3 ，3  |  10 ，3， 4
        // 分页公式：
        // Long totalPages = searchResponseVo.getTotal()%searchParam.getPageSize()==0?(searchResponseVo.getTotal()/searchParam.getPageSize()):(searchResponseVo.getTotal()/searchParam.getPageSize()+1)
        Long totalPages = (searchResponseVo.getTotal() + searchParam.getPageSize() -1)/searchParam.getPageSize();
        searchResponseVo.setTotalPages(totalPages);
        return searchResponseVo;
    }

    private SearchResponseVo parseSearchResult(SearchResponse searchResponse) {
        /*
         private List<SearchResponseTmVo> trademarkList;
         private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
         private List<Goods> goodsList = new ArrayList<>();
         private Long total;
         */
        //my：searchResponse 可以看作是dsl语句的执行结果 SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        // 获取品牌数据 ，品牌数据都是通过聚合获取的！
        //aggregationMap --- aggregations
        // 坑！
        Map<String, Aggregation> aggregationMap = searchResponse.getAggregations().asMap();
        //Aggregation
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) aggregationMap.get("tmIdAgg");
       // Function
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map(bucket -> {//my:Buckets是一个数组，每一个bucket是数组里面的元素,品牌只有一个元素，因为聚合的key只有一组
            // 创建一个品牌对象
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            // 获取key 也就是品牌Id
            String tmId = ((Terms.Bucket) bucket).getKeyAsString();
            searchResponseTmVo.setTmId(Long.parseLong(tmId));

            // 继续获取品牌的名称
            Map<String, Aggregation> idSubMap = ((Terms.Bucket) bucket).getAggregations().getAsMap();

            ParsedStringTerms tmNameAgg = (ParsedStringTerms) idSubMap.get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);
            // 继续获取品牌的URL
            ParsedStringTerms tmLogoUrlAgg = (ParsedStringTerms) idSubMap.get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);
            // 返回数据
            return searchResponseTmVo;
        }).collect(Collectors.toList());
        // 赋值品牌数据
        searchResponseVo.setTrademarkList(trademarkList);
        SearchHits hits = searchResponse.getHits();
        SearchHit[] subHits = hits.getHits();
        //声明一个集合存储Goods
        ArrayList<Goods> goodsList = new ArrayList<>();
        if (subHits!=null && subHits.length>0){
            for (SearchHit subHit : subHits) {
                // 获取到每个source 对应的数据
                String sourceAsString = subHit.getSourceAsString();

                //转化为对象
                Goods goods = JSON.parseObject(sourceAsString, Goods.class);
                // 查询数据的时候，发现商品的名称不是高亮！
                if (subHit.getHighlightFields().get("title")!=null){
                    // 获取对应的高亮数据
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    goods.setTitle(title.toString());
                }
                // 添加到集合！
                goodsList.add(goods);
            }
        }
        searchResponseVo.setGoodsList(goodsList);
        // 平台属性 数据类型是nested ,在nested 下才能获取 attrIdAgg，attrNameAgg，attrValueAgg
        ParsedNested attrAgg = (ParsedNested) aggregationMap.get("attrAgg");
        // Map<String, Aggregation> asMap = attrAgg.getAggregations().getAsMap();
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        // 做平台属性
        List<SearchResponseAttrVo> attrsList = attrIdAgg.getBuckets().stream().map(bucket -> {
            SearchResponseAttrVo responseAttrVO = new SearchResponseAttrVo();
            // 赋值平台属性Id
            Number keyAsNumber = ((Terms.Bucket) bucket).getKeyAsNumber();
            responseAttrVO.setAttrId(keyAsNumber.longValue());
            // 赋值平台属性名称
            Map<String, Aggregation> asMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            // 获取属性名称
            ParsedStringTerms attrNameAgg = (ParsedStringTerms) asMap.get("attrNameAgg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            responseAttrVO.setAttrName(attrName);

            ParsedStringTerms attrValueAgg = (ParsedStringTerms) asMap.get("attrValueAgg");
            // 拉姆达表达式： Terms.Bucket::getKeyAsString 表示获取到map 中的每个key ，获取到key 所对应的值！并返回一个list集合
            List<String> valueList = attrValueAgg.getBuckets().stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList());

            responseAttrVO.setAttrValueList(valueList);

            return responseAttrVO;
        }).collect(Collectors.toList());

        searchResponseVo.setAttrsList(attrsList);
        // 总条数
        searchResponseVo.setTotal(hits.totalHits);

        return searchResponseVo;
    }

    /**
     * 手动编写dsl 语句
     * @param searchParam
     * @return
     */
    private SearchRequest buildQueryDsl(SearchParam searchParam) {

        /**
         * 1.  创建查询器：SearchSourceBuilder 相当于 { query}
         * 2.  根据手动编写的dsl 语句实现！
         */
        // {}
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();//{}
        //searchSourceBuilder.query(boolQueryBuilder); 会把boolQueryBuilder放进searchSourceBuilder的query 中
        // 创建 {query bool}     my:你创建一个boolQueryBuilder 相当于一个"bool": {}你只能点最直接包含的key boolQueryBuilder.filter
        //        // key里面嵌套的json点不到方法，需要借助QueryBuilders创建一个放入进去
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 判断分类Id 是否为空，进行过滤 {query bool filter}
        if (!StringUtils.isEmpty(searchParam.getCategory3Id())){
            //{query bool filter term}
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id",searchParam.getCategory3Id()));
        }

        if (!StringUtils.isEmpty(searchParam.getCategory2Id())){
            // {query bool filter term}
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id",searchParam.getCategory2Id()));
        }

        if (!StringUtils.isEmpty(searchParam.getCategory1Id())){
            // {query bool filter term}
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id",searchParam.getCategory1Id()));
        }

        // 判断是否根据商品名称进行检索
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            // Operator.AND 表示并且的关系  keyword = 小米手机， 那么通过分词器得到 小米，手机。
            // 那么在查询的时候，这条记录必须有小米，和手机才算真正匹配成功！
            boolQueryBuilder.must(QueryBuilders.matchQuery("title",searchParam.getKeyword()).operator(Operator.AND));
        }

        // 判断品牌
        String trademark = searchParam.getTrademark();
        // 数据格式 ：trademark=2:华为
        if (!StringUtils.isEmpty(trademark)){
            // 对字符串进行分割
            // 坑！StringUtils.split(); 不采用！
            // 推荐 org.apache.commons.lang.StringUtils.split()
            String[] split = trademark.split(":");
            // 判断分割之后的数组是否符合条件
            if (split!=null && split.length==2){
                boolQueryBuilder.filter(QueryBuilders.termQuery("tmId",split[0]));
            }
        }


    //平台属性过滤
        String[] props = searchParam.getProps();
        if(props!=null && props.length>0){
            //循环数据
            for (String prop : props) {
                // 每一个prop 的数据格式是什么样的? props=23:4G:运行内存
                String[] split = prop.split(":");
                //格式判断
                if (split!=null && split.length==3){
                    // 需要将平台属性Id，平台属性值，平台属性名 获取到才能过滤
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    // 子查询中的bool
                    BoolQueryBuilder subBoolQuery  = QueryBuilders.boolQuery();
                    // attrs.attrValue
                    subBoolQuery.filter(QueryBuilders.termQuery("attrs.attrId",split[0]));
                    // attrs.attrValue
                    subBoolQuery.filter(QueryBuilders.termQuery("attrs.attrValue",split[1]));

                    // 属于嵌套查询 nested
                    // path = attrs , QueryBuilder = subBoolQuery ScoreMode.None   my:ScoreMode.None几步曲
                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));

                    // 将数据赋值boolQueryBuilder
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        // {query }
        searchSourceBuilder.query(boolQueryBuilder);
        // 分页
        // 100 1 【0，20】 【20，40】
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        // 高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        // 设置高亮字段
        highlightBuilder.field("title");
        // 设置前缀
        highlightBuilder.preTags("<span style=color:red>");
        // 设置后缀
        highlightBuilder.postTags("</span>");
        // 将设置好的高亮对象放入 方法中
        searchSourceBuilder.highlighter(highlightBuilder);
        //  设置排序：
        //  1:hotScore 2:price  order=2:asc  | order=2:desc
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)){
            // 分割字符串
            String[] split = order.split(":");
            // 判断传递过来的参数条件是否合法
            if (split!=null && split.length==2){
                // 判断 用户想按照什么哪个字段进行排序
                String field = null;
                switch (split[0]){
                    case "1":
                        field = "hotScore";
                        break;
                    case "2":
                        field = "price";
                        break;
                }
                // 设置字段升序，降序排列
                searchSourceBuilder.sort(field,"asc".equals(split[1])? SortOrder.ASC:SortOrder.DESC);
            }
            searchSourceBuilder.sort("hotScore",SortOrder.DESC);
        }
        //聚合   my:聚合成一条数据，为了前端显示
        //品牌聚合
        TermsAggregationBuilder tmAggregationBuilder = AggregationBuilders.terms("tmIdAgg").field("tmId").subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));
        searchSourceBuilder.aggregation(tmAggregationBuilder);

        // 平台属性聚合
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        //设置查询的字段都有哪些
        //sourceBuilder.fetchSource(false);
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"},null);
        // 当前的dsl 语句需要在哪个index,type 中执行?
        // index = goods type = info
        SearchRequest searchRequest = new SearchRequest("goods");
        // GET /goods/info/_search
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        //输入dsl 语句
        System.out.println("DSL:"+searchSourceBuilder.toString());
        return searchRequest;
    }
}
