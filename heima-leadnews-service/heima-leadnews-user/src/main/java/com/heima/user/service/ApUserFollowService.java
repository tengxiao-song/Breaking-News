package com.heima.user.service;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.user.UserRelationDto;
import org.springframework.web.bind.annotation.RequestBody;

public interface ApUserFollowService {
    ResponseResult follow(UserRelationDto dto);
}
