/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.config.server.controller;

import com.alibaba.nacos.common.model.RestResult;
import com.alibaba.nacos.config.server.constant.Constants;
import com.alibaba.nacos.config.server.model.capacity.Capacity;
import com.alibaba.nacos.config.server.service.capacity.CapacityService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

/**
 * Capacity Management.
 *
 * @author hexu.hxy
 */
@RestController
@RequestMapping(Constants.CAPACITY_CONTROLLER_PATH)
public class CapacityController {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityController.class);
    
    private final CapacityService capacityService;
    
    @Autowired
    public CapacityController(CapacityService capacityService) {
        this.capacityService = capacityService;
    }
    
    @GetMapping
    public RestResult<Capacity> getCapacity(HttpServletResponse response, @RequestParam(required = false) String group,
            @RequestParam(required = false) String tenant) {
        if (group == null && tenant == null) {
            RestResult<Capacity> restResult = new RestResult<Capacity>();
            response.setStatus(400);
            restResult.setCode(400);
            restResult.setMessage("参数group和tenant不能同时为空");
            return restResult;
        }
        if (group == null && StringUtils.isBlank(tenant)) {
            RestResult<Capacity> restResult = new RestResult<Capacity>();
            response.setStatus(400);
            restResult.setCode(400);
            restResult.setMessage("tenant不能为空字符串");
            return restResult;
        }
        RestResult<Capacity> restResult = new RestResult<Capacity>();
        try {
            response.setStatus(200);
            restResult.setCode(200);
            Capacity capacity = capacityService.getCapacityWithDefault(group, tenant);
            if (capacity == null) {
                LOGGER.warn("[getCapacity] capacity不存在，需初始化 group: {}, tenant: {}", group, tenant);
                capacityService.initCapacity(group, tenant);
                capacity = capacityService.getCapacityWithDefault(group, tenant);
            }
            if (capacity != null) {
                restResult.setData(capacity);
            }
        } catch (Exception e) {
            LOGGER.error("[getCapacity] ", e);
            response.setStatus(500);
            restResult.setCode(500);
            restResult.setMessage(e.getMessage());
        }
        return restResult;
    }
    
    /**
     * Modify group or capacity of tenant, and init record when capacity informations are still initial.
     */
    @PostMapping
    public RestResult<Boolean> updateCapacity(HttpServletResponse response,
            @RequestParam(required = false) String group, @RequestParam(required = false) String tenant,
            @RequestParam(required = false) Integer quota, @RequestParam(required = false) Integer maxSize,
            @RequestParam(required = false) Integer maxAggrCount, @RequestParam(required = false) Integer maxAggrSize) {
        if (StringUtils.isBlank(group) && StringUtils.isBlank(tenant)) {
            capacityService.initAllCapacity();
            RestResult<Boolean> restResult = new RestResult<Boolean>();
            setFailResult(response, restResult, 400);
            restResult.setMessage("Parameters Group and Tenant cannot both be empty");
            return restResult;
        }
        if (quota == null && maxSize == null && maxAggrCount == null && maxAggrSize == null) {
            RestResult<Boolean> restResult = new RestResult<Boolean>();
            setFailResult(response, restResult, 400);
            restResult.setMessage("Parameters quota, maxSize, maxAggrCount, and maxAggrSize cannot both be null");
            return restResult;
        }
        String targetFieldName;
        String targetFieldValue;
        if (tenant == null) {
            targetFieldName = "group";
            targetFieldValue = group;
        } else {
            targetFieldName = "tenant";
            targetFieldValue = tenant;
        }
        RestResult<Boolean> restResult = new RestResult<Boolean>();
        if (StringUtils.isBlank(targetFieldValue)) {
            setFailResult(response, restResult, 400);
            restResult.setMessage(String.format("Parameter %s is null", targetFieldName));
            return restResult;
        }
        try {
            boolean insertOrUpdateResult = capacityService
                    .insertOrUpdateCapacity(group, tenant, quota, maxSize, maxAggrCount, maxAggrSize);
            if (insertOrUpdateResult) {
                setSuccessResult(response, restResult);
                restResult.setMessage(String.format("Successfully updated %s for capacity information config for %s", targetFieldName, targetFieldValue));
                return restResult;
            }
            setFailResult(response, restResult, 500);
            restResult.setMessage(String.format("%s failed to configure an update for capacity information for %s", targetFieldName, targetFieldValue));
            return restResult;
        } catch (Exception e) {
            LOGGER.error("[updateCapacity] ", e);
            setFailResult(response, restResult, 500);
            restResult.setMessage(e.getMessage());
            return restResult;
        }
    }
    
    private void setFailResult(HttpServletResponse response, RestResult<Boolean> restResult, int statusCode) {
        response.setStatus(statusCode);
        restResult.setCode(statusCode);
        restResult.setData(false);
    }
    
    private void setSuccessResult(HttpServletResponse response, RestResult<Boolean> restResult) {
        response.setStatus(200);
        restResult.setCode(200);
        restResult.setData(true);
    }
}
