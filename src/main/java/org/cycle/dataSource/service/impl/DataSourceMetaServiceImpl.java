package org.cycle.dataSource.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.dataSource.dto.ColumnMetaDTO;
import org.cycle.dataSource.dto.SchemaMetaDTO;
import org.cycle.dataSource.dto.TableMetaDTO;
import org.cycle.dataSource.entity.DataSourceEntity;
import org.cycle.dataSource.service.DataSourceMetaService;
import org.cycle.dataSource.service.DataSourceService;
import org.cycle.dataSource.util.JdbcDriverLoader;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.*;

import static org.cycle.dataSource.util.DataSourceUtils.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSourceMetaServiceImpl implements DataSourceMetaService {

    private final DataSourceService dataSourceService;

    @Override
    public List<SchemaMetaDTO> listSchemas(String dataSourceId) {
        DataSourceEntity ds = requireDataSource(dataSourceId);
        try (Connection conn = openConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<String> names = new ArrayList<>();

            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) {
                    String name = safeTrim(rs.getString("TABLE_SCHEM"));
                    if (!name.isEmpty()) {
                        names.add(name);
                    }
                }
            } catch (Exception ignored) {
            }

            if (names.isEmpty()) {
                try (ResultSet rs = meta.getCatalogs()) {
                    while (rs.next()) {
                        String name = safeTrim(rs.getString(1));
                        if (!name.isEmpty()) {
                            names.add(name);
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            names = dedupAndSort(names);
            List<SchemaMetaDTO> result = new ArrayList<>(names.size());
            for (String n : names) {
                SchemaMetaDTO dto = new SchemaMetaDTO();
                dto.setName(n);
                result.add(dto);
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("list schemas failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TableMetaDTO> listTables(String dataSourceId, String schema, String keyword) {
        DataSourceEntity ds = requireDataSource(dataSourceId);
        String sch = safeTrim(schema);
        String kw = safeTrim(keyword);
        String pattern = kw.isEmpty() ? "%" : ("%" + kw + "%");

        try (Connection conn = openConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            List<TableMetaDTO> list = new ArrayList<>();

            List<ResultSet> candidates = new ArrayList<>(2);
            try {
                candidates.add(meta.getTables(null, sch.isEmpty() ? null : sch, pattern, new String[]{"TABLE"}));
            } catch (Exception ignored) {
            }
            if (!sch.isEmpty()) {
                try {
                    candidates.add(meta.getTables(sch, null, pattern, new String[]{"TABLE"}));
                } catch (Exception ignored) {
                }
            }

            Set<String> seen = new HashSet<>();
            for (ResultSet rs : candidates) {
                if (rs == null) {
                    continue;
                }
                try (ResultSet closable = rs) {
                    while (closable.next()) {
                        String name = safeTrim(closable.getString("TABLE_NAME"));
                        if (name.isEmpty()) {
                            continue;
                        }
                        String key = name.toLowerCase();
                        if (seen.contains(key)) {
                            continue;
                        }
                        seen.add(key);
                        TableMetaDTO dto = new TableMetaDTO();
                        dto.setName(name);
                        dto.setType(safeTrim(closable.getString("TABLE_TYPE")));
                        dto.setRemarks(safeTrim(closable.getString("REMARKS")));
                        list.add(dto);
                    }
                }
            }

            list.sort(Comparator.comparing(TableMetaDTO::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("list tables failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ColumnMetaDTO> listColumns(String dataSourceId, String schema, String table) {
        DataSourceEntity ds = requireDataSource(dataSourceId);
        String sch = safeTrim(schema);
        String tbl = safeTrim(table);
        if (tbl.isEmpty()) {
            throw new IllegalArgumentException("table is blank");
        }

        try (Connection conn = openConnection(ds)) {
            DatabaseMetaData meta = conn.getMetaData();
            Set<String> pk = new HashSet<>();
            try (ResultSet rs = meta.getPrimaryKeys(null, sch.isEmpty() ? null : sch, tbl)) {
                while (rs.next()) {
                    String col = safeTrim(rs.getString("COLUMN_NAME"));
                    if (!col.isEmpty()) {
                        pk.add(col.toLowerCase());
                    }
                }
            } catch (Exception ignored) {
            }
            if (!sch.isEmpty() && pk.isEmpty()) {
                try (ResultSet rs = meta.getPrimaryKeys(sch, null, tbl)) {
                    while (rs.next()) {
                        String col = safeTrim(rs.getString("COLUMN_NAME"));
                        if (!col.isEmpty()) {
                            pk.add(col.toLowerCase());
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            List<ColumnMetaDTO> list = new ArrayList<>();
            List<ResultSet> candidates = new ArrayList<>(2);
            try {
                candidates.add(meta.getColumns(null, sch.isEmpty() ? null : sch, tbl, "%"));
            } catch (Exception ignored) {
            }
            if (!sch.isEmpty()) {
                try {
                    candidates.add(meta.getColumns(sch, null, tbl, "%"));
                } catch (Exception ignored) {
                }
            }

            Set<String> seen = new HashSet<>();
            for (ResultSet rs : candidates) {
                if (rs == null) {
                    continue;
                }
                try (ResultSet closable = rs) {
                    while (closable.next()) {
                        String name = safeTrim(closable.getString("COLUMN_NAME"));
                        if (name.isEmpty()) {
                            continue;
                        }
                        String key = name.toLowerCase();
                        if (seen.contains(key)) {
                            continue;
                        }
                        seen.add(key);

                        ColumnMetaDTO dto = new ColumnMetaDTO();
                        dto.setName(name);
                        dto.setTypeName(safeTrim(closable.getString("TYPE_NAME")));
                        dto.setDataType(toInt(closable.getObject("DATA_TYPE")));
                        dto.setColumnSize(toInt(closable.getObject("COLUMN_SIZE")));
                        dto.setDecimalDigits(toInt(closable.getObject("DECIMAL_DIGITS")));
                        dto.setNullable(toInt(closable.getObject("NULLABLE")));
                        dto.setOrdinalPosition(toInt(closable.getObject("ORDINAL_POSITION")));
                        dto.setPrimaryKey(pk.contains(key));
                        list.add(dto);
                    }
                }
            }

            list.sort(Comparator.comparing(ColumnMetaDTO::getOrdinalPosition, Comparator.nullsLast(Integer::compareTo)));
            return list;
        } catch (Exception e) {
            throw new IllegalStateException("list columns failed: " + e.getMessage(), e);
        }
    }

    private DataSourceEntity requireDataSource(String id) {
        String dsId = safeTrim(id);
        if (dsId.isEmpty()) {
            throw new IllegalArgumentException("dataSourceId is blank");
        }
        DataSourceEntity ds = dataSourceService.getById(dsId);
        if (ds == null) {
            throw new IllegalArgumentException("dataSource not found: " + dsId);
        }
        return ds;
    }

    private Connection openConnection(DataSourceEntity ds) throws Exception {
        String driver = safeTrim(ds.getDriverClassName());
        String url = safeTrim(ds.getUrl());
        String username = safeTrim(ds.getUsername());
        String password = ds.getPassword();

        if (isBlank(url)) {
            throw new IllegalArgumentException("url is blank");
        }

        String resolvedDriver = !isBlank(driver) ? driver : inferDriverByUrl(url);
        if (!isBlank(resolvedDriver)) {
            JdbcDriverLoader.loadDriver(resolvedDriver, "E:\\项目\\数据中台\\代码\\dataSpace\\drivers");
        }
        return DriverManager.getConnection(url, username, password);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> dedupAndSort(List<String> names) {
        Set<String> set = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (String n : names) {
            String s = safeTrim(n);
            if (s.isEmpty()) {
                continue;
            }
            String key = s.toLowerCase();
            if (set.add(key)) {
                out.add(s);
            }
        }
        out.sort(String::compareToIgnoreCase);
        return out;
    }
}

