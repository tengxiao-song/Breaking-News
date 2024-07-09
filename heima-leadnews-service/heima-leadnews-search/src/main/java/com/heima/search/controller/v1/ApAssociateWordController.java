package com.heima.search.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.search.dtos.UserSearchDto;
import com.heima.search.service.ApAssociateWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/associate")
public class ApAssociateWordController {

    @Autowired
    private ApAssociateWordService apAssociateWordService;

    @PostMapping("/search")
    public ResponseResult searchAssociateWords(@RequestBody UserSearchDto dto) {
        return apAssociateWordService.searchAssociateWords(dto);
    }
}
