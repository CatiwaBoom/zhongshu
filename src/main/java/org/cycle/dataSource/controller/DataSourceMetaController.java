package org.cycle.dataSource.controller;

import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.dataSource.dto.ColumnMetaDTO;
import org.cycle.dataSource.dto.SchemaMetaDTO;
import org.cycle.dataSource.dto.TableMetaDTO;
import org.cycle.dataSource.service.DataSourceMetaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/datasource/meta")
public class DataSourceMetaController extends BaseController {

    @Resource
    private DataSourceMetaService metaService;

    @GetMapping("/schemas/{id}")
    public Result<List<SchemaMetaDTO>> schemas(@PathVariable("id") String id) {
        try {
            return success(metaService.listSchemas(id), "查询成功");
        } catch (Exception e) {
            log.error("查询 schemas 失败, id={}", id, e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/tables/{id}")
    public Result<List<TableMetaDTO>> tables(@PathVariable("id") String id,
                                            @RequestParam(value = "schema", required = false) String schema,
                                            @RequestParam(value = "keyword", required = false) String keyword) {
        try {
            return success(metaService.listTables(id, schema, keyword), "查询成功");
        } catch (Exception e) {
            log.error("查询 tables 失败, id={}, schema={}", id, schema, e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/columns/{id}")
    public Result<List<ColumnMetaDTO>> columns(@PathVariable("id") String id,
                                              @RequestParam(value = "schema", required = false) String schema,
                                              @RequestParam("table") String table) {
        try {
            return success(metaService.listColumns(id, schema, table), "查询成功");
        } catch (Exception e) {
            log.error("查询 columns 失败, id={}, schema={}, table={}", id, schema, table, e);
            return fail(500, "查询失败: " + e.getMessage());
        }
    }
}

