package org.cycle.user.controller;

import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.user.entity.MenuEntity;
import org.cycle.user.service.MenuService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 菜单管理接口
 */
@RestController
@RequestMapping("/menus")
public class MenuController extends BaseController {

    @Resource
    private MenuService menuService;

    @GetMapping("/tree")
    public Result<List<MenuEntity>> tree() {
        List<MenuEntity> tree = menuService.listTree();
        return success(tree);
    }

    @GetMapping("/{id}")
    public Result<MenuEntity> getById(@PathVariable String id) {
        MenuEntity m = menuService.getById(id);
        if (m == null) return fail(404, "菜单不存在");
        return success(m);
    }

    @PostMapping("")
    public Result<Void> create(@RequestBody MenuEntity req) {
        try {
            if (req.getId() == null || req.getId().trim().isEmpty()) req.setId(UUID.randomUUID().toString());
            boolean ok = menuService.save(req);
            if (ok) return success(null, "创建成功");
            else return fail(500, "创建失败");
        } catch (Exception e) {
            return fail(500, "创建失败：" + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody MenuEntity req) {
        try {
            req.setId(id);
            boolean ok = menuService.updateById(req);
            if (ok) return success(null, "更新成功");
            else return fail(500, "更新失败");
        } catch (Exception e) {
            return fail(500, "更新失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        try {
            boolean ok = menuService.removeById(id);
            if (ok) return success(null, "删除成功");
            else return fail(500, "删除失败");
        } catch (Exception e) {
            return fail(500, "删除失败：" + e.getMessage());
        }
    }
}

