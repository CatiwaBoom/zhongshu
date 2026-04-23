package org.cycle.model.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.dataSource.service.DataSourceService;
import org.cycle.model.ddl.BusinessModelDdlExecutorManager;
import org.cycle.model.dto.BusinessFieldDto;
import org.cycle.model.dto.BusinessModelDto;
import org.cycle.model.ddl.BusinessModelDdlGeneratorManager;
import org.cycle.model.ddl.BusinessModelDdlExecutor;
import org.cycle.model.entity.BusinessFieldEntity;
import org.cycle.model.entity.BusinessModelEntity;
import org.cycle.model.mapper.BusinessFieldMapper;
import org.cycle.model.mapper.BusinessModelMapper;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 业务模型管理 Controller
 * 提供模型的增删改查以及生成建表 DDL（仅返回，不默认执行）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BusinessModelController extends BaseController {

    private final BusinessModelMapper businessModelMapper;
    private final BusinessFieldMapper businessFieldMapper;
    private final BusinessModelDdlGeneratorManager ddlGeneratorManager;
    private final DataSourceService dataSourceService;
    private final BusinessModelDdlExecutorManager ddlExecutorManager;

    @GetMapping("/models")
    public Result<List<BusinessModelEntity>> list() {
        try {
            List<BusinessModelEntity> list = businessModelMapper.selectList(null);
            return success(list, "查询成功");
        } catch (Exception e) {
            log.error("查询业务模型列表失败", e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/models/{id}")
    public Result<BusinessModelDto> get(@PathVariable("id") String id) {
        try {
            BusinessModelEntity model = businessModelMapper.selectById(id);
            if (model == null) {
                return fail(404, "模型未找到");
            }
            List<BusinessFieldEntity> fields = businessFieldMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BusinessFieldEntity>().eq("MODEL_ID", id).orderByAsc("SORT_ORDER"));
            BusinessModelDto dto = new BusinessModelDto();
            dto.setId(model.getId());
            dto.setName(model.getName());
            dto.setTableName(model.getTableName());
            dto.setDescription(model.getDescription());
            List<BusinessFieldDto> fieldDtos = new ArrayList<>();
            for (BusinessFieldEntity f : fields) {
                BusinessFieldDto fd = new BusinessFieldDto();
                fd.setId(f.getId());
                fd.setFieldName(f.getFieldName());
                fd.setColumnName(f.getColumnName());
                fd.setDataType(f.getDataType());
                fd.setLength(f.getLength());
                fd.setIsPrimary(f.getIsPrimary() != null && f.getIsPrimary() == 1);
                fd.setIsNullable(f.getIsNullable() == null || f.getIsNullable() == 1);
                fd.setFieldComment(f.getFieldComment());
                fd.setDefaultValue(f.getDefaultValue());
                fd.setIsUnique(f.getIsUnique() != null && f.getIsUnique() == 1);
                fd.setIsIndexed(f.getIsIndexed() != null && f.getIsIndexed() == 1);
                fd.setSortOrder(f.getSortOrder());
                fieldDtos.add(fd);
            }
            dto.setFields(fieldDtos);
            return success(dto, "查询成功");
        } catch (Exception e) {
            log.error("查询模型失败, id={}", id, e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/models")
    public Result<String> create(@Valid @RequestBody BusinessModelDto dto) {
        try {
            // 简单校验：tableName 唯一
            List<BusinessModelEntity> exists = businessModelMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BusinessModelEntity>().eq("TABLE_NAME", dto.getTableName()));
            if (exists != null && !exists.isEmpty()) {
                return fail(400, "物理表名已存在，请更换");
            }
            BusinessModelEntity model = new BusinessModelEntity();
            model.setId(UUID.randomUUID().toString());
            model.setName(dto.getName());
            model.setTableName(dto.getTableName());
            model.setDescription(dto.getDescription());
            businessModelMapper.insert(model);

            if (dto.getFields() != null) {
                int order = 1;
                for (BusinessFieldDto f : dto.getFields()) {
                    BusinessFieldEntity fe = new BusinessFieldEntity();
                    fe.setId(UUID.randomUUID().toString());
                    fe.setModelId(model.getId());
                    fe.setFieldName(f.getFieldName());
                    fe.setColumnName(f.getColumnName());
                    fe.setDataType(f.getDataType());
                    fe.setLength(f.getLength());
                    fe.setIsPrimary(f.getIsPrimary() ? 1 : 0);
                    fe.setIsNullable(f.getIsNullable() ? 1 : 0);
                    fe.setFieldComment(f.getFieldComment());
                    fe.setDefaultValue(f.getDefaultValue());
                    fe.setIsUnique(f.getIsUnique() != null && f.getIsUnique() ? 1 : 0);
                    fe.setIsIndexed(f.getIsIndexed() != null && f.getIsIndexed() ? 1 : 0);
                    fe.setSortOrder(f.getSortOrder() == null ? order++ : f.getSortOrder());
                    businessFieldMapper.insert(fe);
                }
            }
            return success(model.getId(), "创建成功");
        } catch (Exception e) {
            log.error("创建模型失败", e);
            return fail(500, "创建失败: " + e.getMessage());
        }
    }

    @PutMapping("/models/{id}")
    public Result<String> update(@PathVariable("id") String id, @Valid @RequestBody BusinessModelDto dto) {
        try {
            BusinessModelEntity model = businessModelMapper.selectById(id);
            if (model == null) {
                return fail(404, "模型未找到");
            }
            model.setName(dto.getName());
            model.setTableName(dto.getTableName());
            model.setDescription(dto.getDescription());
            businessModelMapper.updateById(model);

            // 简单实现：删除原字段，重新插入（便于实现字段变更）
            businessFieldMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BusinessFieldEntity>().eq("MODEL_ID", id));
            if (dto.getFields() != null) {
                int order = 1;
                for (BusinessFieldDto f : dto.getFields()) {
                    BusinessFieldEntity fe = new BusinessFieldEntity();
                    fe.setId(UUID.randomUUID().toString());
                    fe.setModelId(id);
                    fe.setFieldName(f.getFieldName());
                    fe.setColumnName(f.getColumnName());
                    fe.setDataType(f.getDataType());
                    fe.setLength(f.getLength());
                    fe.setIsPrimary(f.getIsPrimary() ? 1 : 0);
                    fe.setIsNullable(f.getIsNullable() ? 1 : 0);
                    fe.setFieldComment(f.getFieldComment());
                    fe.setDefaultValue(f.getDefaultValue());
                    fe.setIsUnique(f.getIsUnique() != null && f.getIsUnique() ? 1 : 0);
                    fe.setIsIndexed(f.getIsIndexed() != null && f.getIsIndexed() ? 1 : 0);
                    fe.setSortOrder(f.getSortOrder() == null ? order++ : f.getSortOrder());
                    businessFieldMapper.insert(fe);
                }
            }
            return success(id, "更新成功");
        } catch (Exception e) {
            log.error("更新模型失败, id={}", id, e);
            return fail(500, "更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/models/{id}")
    public Result<Void> delete(@PathVariable("id") String id) {
        try {
            businessFieldMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BusinessFieldEntity>().eq("MODEL_ID", id));
            int r = businessModelMapper.deleteById(id);
            if (r == 0) {
                return fail(404, "模型未找到或删除失败");
            }
            return success(null, "删除成功");
        } catch (Exception e) {
            log.error("删除模型失败, id={}", id, e);
            return fail(500, "删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/models/{id}/generate-ddl")
    public Result<String> generateDdl(@PathVariable("id") String id,
                                      @RequestParam(value = "dsId", required = false) String dsId,
                                      @RequestParam(value = "schema", required = false) String schema,
                                      @RequestParam(value = "execute", required = false, defaultValue = "false") boolean execute) {
        try {
            BusinessModelEntity model = businessModelMapper.selectById(id);
            if (model == null) {
                return fail(404, "模型未找到");
            }
            List<BusinessFieldEntity> fields = businessFieldMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BusinessFieldEntity>().eq("MODEL_ID", id).orderByAsc("SORT_ORDER"));
            // 根据 dsId 推断数据库类型，否则使用默认实现
            String dbType = null;
            if (dsId != null && !dsId.isEmpty()) {
                try {
                    org.cycle.dataSource.entity.DataSourceEntity ds = dataSourceService.getById(dsId);
                    if (ds != null) {
                        // 根据 driverClassName 或 URL 推断类型（简化处理）
                        String driver = ds.getDriverClassName();
                        if (driver != null) {
                            driver = driver.toLowerCase();
                            if (driver.contains("mysql")) dbType = "mysql";
                            else if (driver.contains("dm") || driver.contains("dmjdbc")) dbType = "dm";
                            else if (driver.contains("oracle")) dbType = "dm"; // treat oracle as dm-style for now
                            else if (driver.contains("postgres") || driver.contains("postgresql")) dbType = "postgres";
                        }
                        if (dbType == null) {
                            String url = ds.getUrl();
                            if (url != null) {
                                                if (url.startsWith("jdbc:mysql:")) dbType = "mysql";
                                                else if (url.startsWith("jdbc:dm:")) dbType = "dm";
                                                else if (url.startsWith("jdbc:oracle:")) dbType = "dm";
                                                else if (url.startsWith("jdbc:postgresql:")) dbType = "postgres";
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            org.cycle.model.ddl.BusinessModelDdlGenerator gen = ddlGeneratorManager.getGenerator(dbType);
            if (gen == null) return fail(500, "未找到可用的 DDL 生成器");
            // 如果用户选择了 schema，则临时用 schema 前缀修改表名（例如：SCHEMA.TABLE），以便生成器将带 schema 的表名写入 DDL
            String originalTableName = model.getTableName();
            if (schema != null && !schema.trim().isEmpty()) {
                model.setTableName(schema.trim() + "." + (originalTableName == null ? "" : originalTableName));
            }
            String ddl = gen.generateCreateTable(model, fields);
            // 恢复原始表名，避免影响后续操作
            model.setTableName(originalTableName);

            if (!execute) {
                return success(ddl, "生成成功");
            }

            // 若要求执行 DDL，则必须传入 dsId 并且必须有权限（权限校验由调用方或框架负责，此处仅为示例）
            if (dsId == null || dsId.isEmpty()) {
                return fail(400, "执行 DDL 需要指定 dsId 参数");
            }

            org.cycle.dataSource.entity.DataSourceEntity ds = dataSourceService.getById(dsId);
            if (ds == null) return fail(404, "数据源未找到");

            // 根据 ds 推断 dbType 并选择 executor
            String executorDbType = null;
            String ddriver = ds.getDriverClassName();
            if (ddriver != null) {
                String driverLower = ddriver.toLowerCase();
                if (driverLower.contains("mysql")) executorDbType = "mysql";
                else if (driverLower.contains("dm") || driverLower.contains("dmjdbc")) executorDbType = "dm";
            }
            if (executorDbType == null) {
                String url = ds.getUrl();
                if (url != null) {
                    if (url.startsWith("jdbc:mysql:")) executorDbType = "mysql";
                    else if (url.startsWith("jdbc:dm:")) executorDbType = "dm";
                }
            }

            org.cycle.model.ddl.BusinessModelDdlExecutor executor = ddlExecutorManager.getExecutor(executorDbType);
            if (executor == null) return fail(500, "未找到可用的 DDL 执行器");

            // 执行 DDL（注意：高危操作，应限制权限）
            BusinessModelDdlExecutor.ExecResult res = executor.executeDdl(ds, ddl);
            if (res.success) {
                return success(ddl, "已执行并成功");
            }
            return fail(500, "执行失败: " + res.message);
        } catch (Exception e) {
            log.error("生成 DDL 失败, id={}", id, e);
            return fail(500, "生成失败: " + e.getMessage());
        }
    }

    // 已通过策略模式（BusinessModelDdlGenerator）替代具体数据库类型映射，移除 mapToSqlType
}


