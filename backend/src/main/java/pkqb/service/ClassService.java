package pkqb.service;

import pkqb.pojo.dto.admin.AdminClassRequest;
import pkqb.pojo.entity.ClassEntity;

import java.util.List;

/**
 * 班级服务接口
 */
public interface ClassService {

    /** 查询全部班级 */
    List<ClassEntity> list();

    /** 新增班级 */
    ClassEntity create(AdminClassRequest request);

    /** 修改班级 */
    ClassEntity update(Integer id, AdminClassRequest request);

    /** 删除班级（物理删除，若有账号引用则拒绝） */
    void delete(Integer id);
}
