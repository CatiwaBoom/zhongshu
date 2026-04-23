package org.cycle.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.file.dto.FileObjectVO;
import org.cycle.file.entity.FileObjectEntity;
import org.cycle.file.mapper.FileObjectMapper;
import org.cycle.system.dto.SystemDto;
import org.cycle.system.entity.SystemEntity;
import org.cycle.system.service.SystemService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 外部系统注册管理 Controller
 * 提供 CRUD、附件列表查询、以及实时连通性检测接口
 */
@Slf4j
@RestController
@RequestMapping("/system")
@Validated
public class SystemController extends BaseController {

    @Resource
    private SystemService systemService;

    @Resource
    private FileObjectMapper fileObjectMapper;

    @GetMapping("/{id}")
    public Result<SystemEntity> getById(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) return fail(400, "系统ID不能为空");
        SystemEntity e = systemService.getById(id);
        if (e == null) return fail(404, "系统不存在");
        return success(e);
    }

    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody SystemDto dto) {
        try {
            SystemEntity entity = toEntity(dto);
            boolean ok = systemService.save(entity);
            if (ok) return success(null, "系统新增成功");
            return fail(500, "系统新增失败");
        } catch (Exception e) {
            log.error("新增系统失败", e);
            return fail(500, "新增系统失败: " + e.getMessage());
        }
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable String id, @Valid @RequestBody SystemDto dto) {
        if (id == null || id.trim().isEmpty()) return fail(400, "系统ID不能为空");
        try {
            SystemEntity entity = toEntity(dto);
            entity.setId(id);
            boolean ok = systemService.updateById(entity);
            if (ok) return success(null, "系统修改成功");
            return fail(500, "系统修改失败");
        } catch (Exception e) {
            log.error("修改系统失败", e);
            return fail(500, "修改系统失败: " + e.getMessage());
        }
    }

    @GetMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) return fail(400, "系统ID不能为空");
        try {
            boolean ok = systemService.removeById(id);
            if (ok) return success(null, "系统删除成功");
            return fail(500, "系统删除失败");
        } catch (Exception e) {
            log.error("删除系统失败", e);
            return fail(500, "删除系统失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        try {
            QueryWrapper<SystemEntity> qw = new QueryWrapper<>();
            qw.select("id", "name", "description", "address", "port", "system_code", "created_at", "updated_at");
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim();
                qw.and(w -> w.likeRight("name", kw).or().likeRight("system_code", kw).or().likeRight("address", kw));
            }
            int maxSize = 200;
            int actualSize = Math.min(Math.max(1, size), maxSize);
            Page<SystemEntity> p = new Page<>(Math.max(1, page), actualSize);
            Page<SystemEntity> result = systemService.page(p, qw);
            return success(result);
        } catch (Exception e) {
            log.error("查询系统列表失败", e);
            return fail(500, "查询系统列表失败: " + e.getMessage());
        }
    }

    /**
     * 测试任意 address+port 的连通性（实时检查，不修改数据库）
     */
    @GetMapping("/status")
    public Result<?> status(@RequestParam(value = "address") String address,
                            @RequestParam(value = "port") Integer port,
                            @RequestParam(value = "timeout", defaultValue = "3000") Integer timeoutMillis) {
        if (address == null || address.trim().isEmpty() || port == null) return fail(400, "address/port不能为空");
        try {
            boolean ok = systemService.checkStatus(address, port, timeoutMillis);
            return success(ok);
        } catch (Exception e) {
            log.error("检测系统连通性失败", e);
            return fail(500, "检测系统连通性失败: " + e.getMessage());
        }
    }

    /**
     * 根据系统ID返回附件列表（使用 file 模块的数据）
     */
    @GetMapping("/{id}/attachments")
    public Result<List<FileObjectVO>> attachments(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) return fail(400, "系统ID不能为空");
        try {
            SystemEntity e = systemService.getById(id);
            if (e == null) return fail(404, "系统不存在");
            String aid = e.getAttachmentIds();
            if (aid == null || aid.trim().isEmpty()) return success(new ArrayList<>());
            List<String> ids = Arrays.stream(aid.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            List<FileObjectVO> result = new ArrayList<>();
            for (String fid : ids) {
                FileObjectEntity obj = fileObjectMapper.selectById(fid);
                if (obj != null) {
                    FileObjectVO vo = new FileObjectVO();
                    vo.setId(obj.getId());
                    vo.setFileName(obj.getFileName());
                    vo.setContentType(obj.getContentType());
                    vo.setFileSize(obj.getFileSize());
                    vo.setFileMd5(obj.getFileMd5());
                    vo.setTotalChunks(obj.getTotalChunks());
                    vo.setUploadCount(obj.getUploadCount());
                    vo.setCreatedAt(obj.getCreatedAt());
                    result.add(vo);
                }
            }
            return success(result);
        } catch (Exception ex) {
            log.error("查询附件列表失败", ex);
            return fail(500, "查询附件列表失败: " + ex.getMessage());
        }
    }

    // ========== 辅助方法 ==========
    private SystemEntity toEntity(SystemDto dto) {
        SystemEntity e = new SystemEntity();
        e.setId(dto.getId());
        e.setName(dto.getName());
        e.setDescription(dto.getDescription());
        e.setAddress(dto.getAddress());
        e.setPort(dto.getPort());
        e.setSystemCode(dto.getSystemCode());
        if (dto.getAttachmentIds() != null && !dto.getAttachmentIds().isEmpty()) {
            String joined = String.join(",", dto.getAttachmentIds());
            e.setAttachmentIds(joined);
        } else {
            e.setAttachmentIds(null);
        }
        return e;
    }
}


