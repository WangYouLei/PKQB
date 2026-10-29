package pkqb.pojo.dto;

import lombok.Data;
import pkqb.pojo.entity.WrongQuestionEntity;

import java.util.List;

/**
 * 错题列表分页响应 DTO
 */
@Data
public class WrongQuestionPageDTO {

    /** 当前页数据 */
    private List<WrongQuestionEntity> list;

    /** 总记录数 */
    private long total;

    /** 当前页码 */
    private long page;

    /** 每页条数 */
    private long pageSize;
}
