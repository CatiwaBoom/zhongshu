package org.cycle.seatunnel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.cycle.dataSource.entity.DataSourceEntity;
import org.cycle.dataSource.service.DataSourceService;
import org.cycle.seatunnel.entity.DataSyncTaskEntity;
import org.cycle.seatunnel.entity.SeatunnelPipelineEntity;
import org.cycle.seatunnel.generator.JdbcToJdbcConfigGenerator;
import org.cycle.seatunnel.mapper.DataSyncTaskMapper;
import org.cycle.seatunnel.service.DataSyncTaskService;
import org.cycle.seatunnel.service.SeatunnelPipelineService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataSyncTaskServiceImpl extends ServiceImpl<DataSyncTaskMapper, DataSyncTaskEntity> implements DataSyncTaskService {

    private final DataSourceService dataSourceService;
    private final SeatunnelPipelineService pipelineService;

    private final JdbcToJdbcConfigGenerator generator = new JdbcToJdbcConfigGenerator();

    @Override
    public DataSyncTaskEntity createTask(DataSyncTaskEntity task) {
        normalize(task);
        DataSourceEntity source = requireDs(task.getSourceDsId());
        DataSourceEntity sink = requireDs(task.getSinkDsId());
        String conf = generator.generate(task, source, sink);

        SeatunnelPipelineEntity pipeline = new SeatunnelPipelineEntity();
        pipeline.setName(task.getName());
        pipeline.setConfigFormat("hocon");
        pipeline.setConfigContent(conf);
        pipeline.setExecMode("cluster");
        pipeline.setClusterName(null);
        pipeline.setStatus(task.getStatus());
        pipeline.setRemark(task.getRemark());
        pipelineService.save(pipeline);

        task.setPipelineId(pipeline.getId());
        save(task);
        return task;
    }

    @Override
    public DataSyncTaskEntity updateTask(String id, DataSyncTaskEntity task) {
        DataSyncTaskEntity exist = getById(id);
        if (exist == null) {
            throw new IllegalArgumentException("task not found: " + id);
        }

        task.setId(id);
        normalize(task);

        String pipelineId = safeTrim(task.getPipelineId());
        if (pipelineId.isEmpty()) {
            pipelineId = safeTrim(exist.getPipelineId());
            task.setPipelineId(pipelineId);
        }

        DataSourceEntity source = requireDs(task.getSourceDsId());
        DataSourceEntity sink = requireDs(task.getSinkDsId());
        String conf = generator.generate(task, source, sink);

        if (!pipelineId.isEmpty()) {
            SeatunnelPipelineEntity pipeline = new SeatunnelPipelineEntity();
            pipeline.setId(pipelineId);
            pipeline.setName(task.getName());
            pipeline.setConfigFormat("hocon");
            pipeline.setConfigContent(conf);
            pipeline.setStatus(task.getStatus());
            pipeline.setRemark(task.getRemark());
            pipelineService.updateById(pipeline);
        }

        updateById(task);
        return task;
    }

    @Override
    public String generateConfig(String taskId) {
        DataSyncTaskEntity task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task not found: " + taskId);
        }
        DataSourceEntity source = requireDs(task.getSourceDsId());
        DataSourceEntity sink = requireDs(task.getSinkDsId());
        return generator.generate(task, source, sink);
    }

    @Override
    public void refreshPipelineConfig(String taskId) {
        DataSyncTaskEntity task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("task not found: " + taskId);
        }
        String pipelineId = safeTrim(task.getPipelineId());
        if (pipelineId.isEmpty()) {
            throw new IllegalArgumentException("task pipelineId is blank: " + taskId);
        }
        DataSourceEntity source = requireDs(task.getSourceDsId());
        DataSourceEntity sink = requireDs(task.getSinkDsId());
        String conf = generator.generate(task, source, sink);

        SeatunnelPipelineEntity pipeline = new SeatunnelPipelineEntity();
        pipeline.setId(pipelineId);
        pipeline.setName(task.getName());
        pipeline.setConfigFormat("hocon");
        pipeline.setConfigContent(conf);
        pipeline.setStatus(task.getStatus());
        pipeline.setRemark(task.getRemark());
        pipelineService.updateById(pipeline);
    }

    private DataSourceEntity requireDs(String id) {
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

    private void normalize(DataSyncTaskEntity task) {
        if (safeTrim(task.getName()).isEmpty()) {
            task.setName("同步任务");
        }
        if (safeTrim(task.getSaveMode()).isEmpty()) {
            task.setSaveMode("APPEND_DATA");
        }
        if (task.getParallelism() == null || task.getParallelism() <= 0) {
            task.setParallelism(1);
        }
        if (task.getStatus() == null) {
            task.setStatus(1);
        }
        task.setSourceSchema(emptyToNull(task.getSourceSchema()));
        task.setSinkSchema(emptyToNull(task.getSinkSchema()));
    }

    private String emptyToNull(String v) {
        String s = safeTrim(v);
        return s.isEmpty() ? null : s;
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}

