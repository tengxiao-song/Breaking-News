package com.heima.search.service.impl;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.search.dtos.HistorySearchDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.search.pojos.ApUserSearch;
import com.heima.search.service.ApUserSearchService;
import com.heima.utils.thread.AppThreadLocalUtil;
import com.mongodb.client.result.DeleteResult;
import com.sun.tools.internal.ws.wsdl.document.http.HTTPConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class ApUserSearchServiceImpl implements ApUserSearchService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    @Async
    public void insert(String keyword, Integer userId) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 查询用户搜索的关键字是否存在
        Query query = Query.query(Criteria.where("userId").is(userId).and("keyword").is(keyword));
        ApUserSearch apUserSearch = mongoTemplate.findOne(query, ApUserSearch.class);

        // 如果存在则更新时间
        if (apUserSearch != null) {
            apUserSearch.setCreatedTime(new Date());
            mongoTemplate.save(apUserSearch);
        } else {
            // 如果不存在则插入, 判断历史记录是否超过10条, 超过则删除最早的一条
            apUserSearch = new ApUserSearch();
            apUserSearch.setUserId(userId);
            apUserSearch.setKeyword(keyword);
            apUserSearch.setCreatedTime(new Date());

            Query queryOld = Query.query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC,"createdTime"));
            List<ApUserSearch> apUserSearches = mongoTemplate.find(queryOld, ApUserSearch.class);

            if (apUserSearches == null || apUserSearches.size() < 10) {
                mongoTemplate.save(apUserSearch);
            } else {
                ApUserSearch lastSearch = apUserSearches.get(apUserSearches.size() - 1);
                mongoTemplate.findAndReplace(Query.query(Criteria.where("id").is(lastSearch.getId())), apUserSearch);
            }
        }
    }

    @Override
    public ResponseResult findUserSearch() {
        // 获取用户信息
        ApUser user = AppThreadLocalUtil.getUser();
        if (user == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        List<ApUserSearch> apUserSearches = mongoTemplate.find(
                Query.query(Criteria.where("userId").is(user.getId())).with(Sort.by(Sort.Direction.DESC, "createdTime")), ApUserSearch.class);
        return ResponseResult.okResult(apUserSearches);
    }

    @Override
    public ResponseResult delUserSearch(HistorySearchDto dto) {
        if (dto.getId() == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        String id = dto.getId();
        Integer userId = AppThreadLocalUtil.getUser().getId();
        mongoTemplate.remove(Query.query(Criteria.where("id").is(id).and("userId").is(userId)), ApUserSearch.class);
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }
}
