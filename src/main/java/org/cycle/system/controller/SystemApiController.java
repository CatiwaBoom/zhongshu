package org.cycle.system.controller;

import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.system.dto.SystemApiDto;
import org.cycle.system.entity.SystemApiEntity;
import org.cycle.system.service.SystemApiService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

/**
 * 外部系统下的接口管理
 */
@Slf4j
@RestController
@RequestMapping("/system/api")
@Validated
public class SystemApiController extends BaseController {

    @Resource
    private SystemApiService systemApiService;

    /** 根据 systemId 查询该系统下所有接口 */
    @GetMapping("/list")
    public Result<List<SystemApiEntity>> listBySystemId(@RequestParam("systemId") String systemId) {
        if (systemId == null || systemId.trim().isEmpty()) return fail(400, "systemId 不能为空");
        try {
            List<SystemApiEntity> list = systemApiService.listBySystemId(systemId);
            return success(list);
        } catch (Exception e) {
            log.error("查询系统接口列表失败", e);
            return fail(500, "查询系统接口列表失败: " + e.getMessage());
        }
    }

    /** 发布接口（新增） */
    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody SystemApiDto dto) {
        try {
            SystemApiEntity entity = toEntity(dto);
            boolean ok = systemApiService.save(entity);
            if (ok) return success(null, "接口发布成功");
            return fail(500, "接口发布失败");
        } catch (Exception e) {
            log.error("发布接口失败", e);
            return fail(500, "发布接口失败: " + e.getMessage());
        }
    }

    /** 编辑接口 */
    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody SystemApiDto dto) {
        if (id == null || id.trim().isEmpty()) return fail(400, "接口ID不能为空");
        try {
            SystemApiEntity entity = toEntity(dto);
            entity.setId(id);
            boolean ok = systemApiService.updateById(entity);
            if (ok) return success(null, "接口修改成功");
            return fail(500, "接口修改失败");
        } catch (Exception e) {
            log.error("修改接口失败", e);
            return fail(500, "修改接口失败: " + e.getMessage());
        }
    }

    /** 删除接口 */
    @GetMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) return fail(400, "接口ID不能为空");
        try {
            boolean ok = systemApiService.removeById(id);
            if (ok) return success(null, "接口删除成功");
            return fail(500, "接口删除失败");
        } catch (Exception e) {
            log.error("删除接口失败", e);
            return fail(500, "删除接口失败: " + e.getMessage());
        }
    }

    /** 查看接口详情 */
    @GetMapping("/{id}")
    public Result<SystemApiEntity> getById(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) return fail(400, "接口ID不能为空");
        try {
            SystemApiEntity e = systemApiService.getById(id);
            if (e == null) return fail(404, "接口不存在");
            return success(e);
        } catch (Exception ex) {
            log.error("查询接口详情失败", ex);
            return fail(500, "查询接口详情失败: " + ex.getMessage());
        }
    }

    // ========== 辅助方法 ==========
    private SystemApiEntity toEntity(SystemApiDto dto) {
        SystemApiEntity e = new SystemApiEntity();
        e.setId(dto.getId());
        e.setSystemId(dto.getSystemId());
        e.setApiName(dto.getApiName());
        e.setMethod(dto.getMethod());
        e.setUrl(dto.getUrl());
        e.setDescription(dto.getDescription());
        e.setRequestExample(dto.getRequestExample());
        e.setResponseExample(dto.getResponseExample());
        e.setReqFieldComment(dto.getReqFieldComment());
        e.setResFieldComment(dto.getResFieldComment());
        e.setAttachmentIds(dto.getAttachmentIds());
        return e;
    }
}

