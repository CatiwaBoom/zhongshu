package org.cycle.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.cycle.user.entity.MenuEntity;

import java.util.List;

public interface MenuService extends IService<MenuEntity> {
    List<MenuEntity> listTree();
}

