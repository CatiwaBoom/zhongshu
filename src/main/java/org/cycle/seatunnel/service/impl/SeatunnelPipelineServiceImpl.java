package org.cycle.seatunnel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.cycle.seatunnel.entity.SeatunnelPipelineEntity;
import org.cycle.seatunnel.mapper.SeatunnelPipelineMapper;
import org.cycle.seatunnel.service.SeatunnelPipelineService;
import org.springframework.stereotype.Service;

@Service
public class SeatunnelPipelineServiceImpl extends ServiceImpl<SeatunnelPipelineMapper, SeatunnelPipelineEntity> implements SeatunnelPipelineService {
}
