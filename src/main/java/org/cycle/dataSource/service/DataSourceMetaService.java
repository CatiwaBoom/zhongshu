package org.cycle.dataSource.service;

import org.cycle.dataSource.dto.ColumnMetaDTO;
import org.cycle.dataSource.dto.SchemaMetaDTO;
import org.cycle.dataSource.dto.TableMetaDTO;

import java.util.List;

public interface DataSourceMetaService {
    List<SchemaMetaDTO> listSchemas(String dataSourceId);

    List<TableMetaDTO> listTables(String dataSourceId, String schema, String keyword);

    List<ColumnMetaDTO> listColumns(String dataSourceId, String schema, String table);
}
