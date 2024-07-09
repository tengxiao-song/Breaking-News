package com.heima.user.service.impl;

import com.heima.common.constans.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.UserRelationDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.service.ApUserFollowService;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ApUserFollowServiceImpl implements ApUserFollowService {

    @Autowired
    private CacheService cacheService;

    @Override
    public ResponseResult follow(UserRelationDto dto) {
        if (dto == null || dto.getAuthorId() == null || dto.getOperation() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        if (dto.getOperation() == 0) {
            // 关注
            cacheService.zAdd(BehaviorConstants.APUSER_FOLLOW_RELATION + user.getId(), dto.getAuthorId().toString(), System.currentTimeMillis());
            cacheService.zAdd(BehaviorConstants.APUSER_FANS_RELATION + dto.getAuthorId(), user.getId().toString(), System.currentTimeMillis());
        } else {
            cacheService.zRemove(BehaviorConstants.APUSER_FOLLOW_RELATION + user.getId(), dto.getAuthorId().toString());
            cacheService.zRemove(BehaviorConstants.APUSER_FANS_RELATION + dto.getAuthorId(), user.getId().toString());
        }
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
