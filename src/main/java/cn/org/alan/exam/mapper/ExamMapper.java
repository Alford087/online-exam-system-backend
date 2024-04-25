package cn.org.alan.exam.mapper;

import cn.org.alan.exam.model.entity.Exam;
import cn.org.alan.exam.model.form.exam.ExamAddForm;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 *  Mapper 接口
 *
 * @author Alan
 * @since 2024-03-21
 */
public interface ExamMapper extends BaseMapper<Exam> {

    int delExam(List<Integer> examIds);

    int delExamGrade(List<Integer> examIds);

    int delExamRepo(List<Integer> examIds);

    int delExamQue(List<Integer> examIds);

    /**
     * 删除用户创建的考试
     * @param userIds 用户id列表
     * @return 影响数据库记录数
     */
    Integer deleteByUserIds(List<Integer> userIds);
}
