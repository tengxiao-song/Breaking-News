package com.heima.article.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.article.service.ArticleCollectionService;
import com.heima.common.constans.BehaviorConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.article.dtos.CollectionBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.pojos.ApUser;
import com.heima.utils.thread.AppThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ArticleCollectionServiceImpl implements ArticleCollectionService {

    @Autowired
    private CacheService cacheService;

    @Override
    public ResponseResult collect(CollectionBehaviorDto dto) {
        if (dto == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        if (dto.getOperation() == 0) {
            Object o = cacheService.hGet(BehaviorConstants.COLLECTION_BEHAVIOR + user.getId(), dto.getEntryId().toString());
            if (o != null) {
                return ResponseResult.errorResult(AppHttpCodeEnum.DATA_EXIST, "已收藏");
            }
            cacheService.hPut(BehaviorConstants.COLLECTION_BEHAVIOR + user.getId(), dto.getEntryId().toString(), JSON.toJSONString(dto));
        } else {
            cacheService.hDelete(BehaviorConstants.COLLECTION_BEHAVIOR + user.getId(), dto.getEntryId().toString());
        }
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
