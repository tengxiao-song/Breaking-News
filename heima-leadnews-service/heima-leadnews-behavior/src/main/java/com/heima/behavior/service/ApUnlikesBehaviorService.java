package com.heima.behavior.service;

import com.heima.model.behavior.dtos.UnLikesBehaviorDto;
import com.heima.model.common.dtos.ResponseResult;
import org.springframework.web.bind.annotation.RequestBody;

public interface ApUnlikesBehaviorService {

    ResponseResult saveUnLikesBehavior(UnLikesBehaviorDto dto);
}
