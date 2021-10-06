package com.atguigu.gmall.user.controller;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sound.sampled.Line;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 用户认证接口
 * </p>
 *
 */
@RestController
@RequestMapping("/api/user/passport")
public class PassportApiController {
    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    // 数据接口，给 web-all 使用的！
    // 接收json 数据 转化为 UserInfo
    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo, HttpServletRequest request, HttpServletResponse response) {
        UserInfo info = userService.login(userInfo);
        // 登录成功了！
        if (info != null){
            String token = UUID.randomUUID().toString();
            HashMap<String, Object> map = new HashMap<>();
            // 登录成功处理的业务！
            map.put("nickName",info.getNickName());
            map.put("token",token);

            //  准备往缓存存储数据
            //  redis 数据类型 String ，key 的规则：
            //  Ip: 为了防止盗用token！
            //  存储userId,Ip 地址。
            //  user:login:
            String userKey = RedisConst.USER_LOGIN_KEY_PREFIX+token;
            JSONObject userJson = new JSONObject();
            userJson.put("userId",info.getId().toString());
            userJson.put("ip", IpUtil.getIpAddress(request));
            redisTemplate.opsForValue().set(userKey, JSON.toJSONString(userJson),RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);

            //返回map
            return Result.ok(map);
        }else {
            // 登录失败！提示信息。
            return Result.fail().message("登录失败！");
        }
    }

    /**
     * 退出登录
     * @param request
     * @return
     */
    @GetMapping("logout")
    public Result logout(HttpServletRequest request){
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX + request.getHeader("token"));
        return Result.ok();
    }


}
