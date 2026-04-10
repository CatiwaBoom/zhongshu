package org.cycle.dataSource.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.cycle.dataSource.entity.DataSourceEntity;
import org.cycle.dataSource.service.DataSourceService;
import org.cycle.common.controller.Result;
import org.cycle.common.controller.BaseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 数据源管理Controller
 * 继承BaseController，复用统一响应格式
 */
@Slf4j
@RestController
@RequestMapping("/datasource")
public class DataSourceController extends BaseController {

    @Resource
    private DataSourceService dataSourceService;

    /**
     * 根据ID查询数据源详情
     * @param id 数据源ID
     * @return 统一格式响应
     */
    @GetMapping("/{id}")
    public Result<DataSourceEntity> getById(@PathVariable String id) {
        // 参数校验
        if (id == null || id.trim().isEmpty()) {
            // 失败响应：参数错误（400）
            return fail(400, "数据源ID不能为空");
        }

        DataSourceEntity dataSource = dataSourceService.getById(id);
        if (dataSource != null) {
            // 成功响应：带数据
            return success(dataSource, "数据源详情查询成功");
        } else {
            // 失败响应：资源不存在（404）
            return fail(404, "数据源不存在");
        }
    }

    /**
     * 新增数据源
     * @param dataSource 数据源实体
     * @return 统一格式响应
     */
    @PostMapping("/add")
    public Result<Void> add(@RequestBody DataSourceEntity dataSource) {
        try {
            // 调用业务层新增
            boolean saveSuccess = dataSourceService.save(dataSource);
            if (saveSuccess) {
                // 成功响应：无数据+自定义提示
                return success(null, "数据源新增成功");
            } else {
                return fail(5002, "数据源新增失败");
            }
        } catch (Exception e) {
            log.error("新增数据源失败", e);
            return fail(500, "新增数据源失败：" + e.getMessage());
        }
    }

    /**
     * 修改数据源
     * @param id 数据源ID
     * @param dataSource 数据源实体
     * @return 统一格式响应
     */
    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody DataSourceEntity dataSource) {
        // 参数校验
        if (id == null || id.trim().isEmpty()) {
            return fail(400, "数据源ID不能为空");
        }
        try {
            // 绑定ID
            dataSource.setId(id);
            boolean updateSuccess = dataSourceService.updateById(dataSource);
            if (updateSuccess) {
                return success(null, "数据源修改成功");
            } else {
                return fail(5003, "数据源修改失败");
            }
        } catch (Exception e) {
            log.error("修改数据源失败", e);
            return fail(500, "修改数据源失败：" + e.getMessage());
        }
    }

    /**
     * 删除数据源
     * @param id 数据源ID
     * @return 统一格式响应
     */
    @GetMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) {
            return fail(400, "数据源ID不能为空");
        }
        try {
            boolean deleteSuccess = dataSourceService.removeById(id);
            if (deleteSuccess) {
                return success(null, "数据源删除成功");
            } else {
                return fail(5004, "数据源删除失败");
            }
        } catch (Exception e) {
            log.error("删除数据源失败", e);
            return fail(500, "删除数据源失败：" + e.getMessage());
        }
    }

    /**
     * 测试数据源连接
     * @param id 数据源ID
     * @return 统一格式响应
     */
    @GetMapping("/test/connect/{id}")
    public Result<Void> testConnect(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) {
            return fail(400, "数据源ID不能为空");
        }
        try {
            boolean connectSuccess = dataSourceService.testConnect(id);
            if (connectSuccess) {
                return success(null, "数据源连接成功");
            } else {
                return fail(5005, "数据源连接失败");
            }
        } catch (Exception e) {
            log.error("测试数据源连接失败", e);
            return fail(500, "测试数据源连接失败：" + e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "connectivity", required = false) String connectivity,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "compress", defaultValue = "false") Boolean compress
    ) {
        try {
            QueryWrapper<DataSourceEntity> qw = new QueryWrapper<>();
            // 只查询必要字段
            qw.select("id", "name", "driver_class_name", "url", "username", "status", "connectivity", "created_at", "updated_at");

            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim();
                qw.and(w -> w.likeRight("name", kw)
                        .or().likeRight("driver_class_name", kw)
                        .or().likeRight("url", kw)
                        .or().likeRight("username", kw));
            }

            // type is a fuzzy match against driverClassName or url (frontend sends values like 'MySQL')
            if (type != null && !type.trim().isEmpty()) {
                String t = type.trim().toLowerCase();
                qw.and(w -> w.likeRight("driver_class_name", t).or().likeRight("url", t));
            }

            // connectivity filter: frontend may send 'connected', 'abnormal', 'disconnected'
            if (connectivity != null && !connectivity.trim().isEmpty()) {
                String c = connectivity.trim();
                if ("connected".equals(c)) {
                    qw.eq("connectivity", 1);
                } else if ("abnormal".equals(c)) {
                    // abnormal: connectivity != 1
                    qw.ne("connectivity", 1);
                } else if ("disconnected".equals(c)) {
                    qw.ne("connectivity", 1);
                }
            }

            // 限制最大页面大小，防止前端请求过大的数据量
            int maxSize = 100;
            int actualSize = Math.min(Math.max(1, size), maxSize);
            Page<DataSourceEntity> p = new Page<>(Math.max(1, page), actualSize);
            Page<DataSourceEntity> result = dataSourceService.page(p, qw);

            if (compress) {
                // 构建压缩的数据结构
                java.util.Map<String, Object> compressed = new java.util.HashMap<>();
                compressed.put("total", result.getTotal());
                compressed.put("current", result.getCurrent());
                compressed.put("size", result.getSize());
                compressed.put("pages", result.getPages());
                compressed.put("data", result.getRecords());
                return success(compressed, "数据源列表查询成功");
            }

            return success(result, "数据源列表查询成功");
        } catch (Exception e) {
            log.error("查询数据源列表失败", e);
            return fail(500, "查询数据源列表失败：" + e.getMessage());
        }
    }
}