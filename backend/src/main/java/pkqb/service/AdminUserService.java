package pkqb.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import pkqb.pojo.dto.admin.AdminUserCreateRequest;
import pkqb.pojo.dto.admin.AdminUserQueryRequest;
import pkqb.pojo.dto.admin.AdminUserUpdateRequest;
import pkqb.pojo.dto.admin.AdminUserVO;

/**
 * 管理端账号服务接口
 */
public interface AdminUserService {

    /** 分页查询账号 */
    IPage<AdminUserVO> page(AdminUserQueryRequest request);

    /** 新增账号 */
    void create(AdminUserCreateRequest request);

    /** 修改账号 */
    void update(Long id, AdminUserUpdateRequest request);

    /** 删除账号（逻辑删除） */
    void delete(Long id);

    /** 重置密码 */
    void resetPassword(Long id, String newPassword);
}
