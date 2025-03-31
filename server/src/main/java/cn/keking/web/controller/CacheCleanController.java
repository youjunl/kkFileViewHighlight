package cn.keking.web.controller;

import cn.keking.model.ReturnResponse;
import cn.keking.service.cache.CacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CacheCleanController {

    private final CacheService cacheService;

    public CacheCleanController(CacheService cacheService) {
        this.cacheService = cacheService;
    }


    @GetMapping("/cleanCache")
    public ReturnResponse<?> clean() {
        cacheService.cleanCache();
        return ReturnResponse.success();
    }

}
