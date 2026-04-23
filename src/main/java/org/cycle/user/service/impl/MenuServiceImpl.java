package org.cycle.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.cycle.user.entity.MenuEntity;
import org.cycle.user.mapper.MenuMapper;
import org.cycle.user.service.MenuService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuServiceImpl extends ServiceImpl<MenuMapper, MenuEntity> implements MenuService {

    @Override
    public List<MenuEntity> listTree() {
        List<MenuEntity> all = this.list();
        Map<String, MenuEntity> idMap = new HashMap<>();
        for (MenuEntity m : all) {
            m.setChildren(new ArrayList<>());
            idMap.put(m.getId(), m);
        }

        List<MenuEntity> roots = new ArrayList<>();
        for (MenuEntity m : all) {
            String pid = m.getParentId();
            if (pid == null || pid.trim().isEmpty() || !idMap.containsKey(pid)) {
                roots.add(m);
            } else {
                MenuEntity parent = idMap.get(pid);
                parent.getChildren().add(m);
            }
        }

        // sort children by orderNum
        sortTree(roots);
        return roots;
    }

    private void sortTree(List<MenuEntity> nodes) {
        if (nodes == null || nodes.isEmpty()) return;
        nodes.sort((a, b) -> Integer.compare(a.getOrderNum() == null ? 0 : a.getOrderNum(), b.getOrderNum() == null ? 0 : b.getOrderNum()));
        for (MenuEntity n : nodes) {
            sortTree(n.getChildren());
        }
    }
}

