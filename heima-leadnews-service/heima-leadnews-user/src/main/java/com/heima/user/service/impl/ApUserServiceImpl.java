package com.heima.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.mapper.ApUserMapper;
import com.heima.user.service.ApUserService;
import com.heima.utils.common.AppJwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class ApUserServiceImpl extends ServiceImpl<ApUserMapper, ApUser> implements ApUserService {

    private final ApUserMapper apUserMapper;

    @Override
    public ResponseResult login(LoginDto dto) {
        // 正常登陆 用户名密码登陆
        if (StringUtils.isNotBlank(dto.getPhone()) && StringUtils.isNotBlank(dto.getPassword())) {
            // 根据手机号查询用户
            ApUser user = getOne(Wrappers.<ApUser>lambdaQuery().eq(ApUser::getPhone, dto.getPhone()));
            // 判断用户是否存在
            if (user == null) {
                return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST, "用户不存在");
            }
            // 判断密码是否正确
            String salt = user.getSalt();
            String password = dto.getPassword();
            String pswd = DigestUtils.md5Hex(password + salt);
            if (!pswd.equals(user.getPassword())) {
                return ResponseResult.errorResult(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);
            }
            // 生成token
            String token = AppJwtUtil.getToken(Long.valueOf(user.getId()));
            HashMap<String, Object> map = new HashMap<>();
            map.put("token", token);
            user.setSalt("");
            user.setPassword("");
            map.put("user", user);
            return ResponseResult.okResult(map);
        } else {
            // 游客登陆
            HashMap<String, Object> map = new HashMap<>();
            map.put("token", AppJwtUtil.getToken(0L));
            return ResponseResult.okResult(map);
        }
    }
}
