package pkqb.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pkqb.common.Result;
import pkqb.pojo.dto.admin.AdminClassRequest;
import pkqb.pojo.entity.ClassEntity;
import pkqb.service.ClassService;

import java.util.List;

/**
 * 管理端班级管理控制器
 * 提供班级查询、新增、修改、删除（物理删除，有账号引用时拒绝）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/class")
@RequiredArgsConstructor
@Tag(name = "管理端-班级管理", description = "班级增删改查")
public class AdminClassController {

    private final ClassService classService;

    @GetMapping
    @Operation(summary = "查询全部班级")
    public Result<List<ClassEntity>> list() {
        return Result.success(classService.list());
    }

    @PostMapping
    @Operation(summary = "新增班级")
    public Result<ClassEntity> create(@Valid @RequestBody AdminClassRequest request) {
        return Result.success(classService.create(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改班级")
    public Result<ClassEntity> update(@PathVariable Integer id, @Valid @RequestBody AdminClassRequest request) {
        return Result.success(classService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除班级", description = "物理删除，若班级下仍有账号则拒绝")
    public Result<Void> delete(@PathVariable Integer id) {
        classService.delete(id);
        return Result.success("删除成功", null);
    }
}
