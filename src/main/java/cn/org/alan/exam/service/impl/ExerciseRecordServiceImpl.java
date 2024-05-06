package cn.org.alan.exam.service.impl;

import cn.org.alan.exam.common.result.Result;
import cn.org.alan.exam.converter.QuestionConverter;
import cn.org.alan.exam.converter.RecordConverter;
import cn.org.alan.exam.mapper.*;
import cn.org.alan.exam.model.entity.*;
import cn.org.alan.exam.model.vo.exercise.QuestionSheetVO;
import cn.org.alan.exam.model.vo.record.ExamRecordDetailVO;
import cn.org.alan.exam.model.vo.record.ExamRecordVO;
import cn.org.alan.exam.model.vo.record.ExerciseRecordDetailVO;
import cn.org.alan.exam.model.vo.record.ExerciseRecordVO;
import cn.org.alan.exam.model.vo.userbook.AddBookAnswerVO;
import cn.org.alan.exam.service.IExerciseRecordService;
import cn.org.alan.exam.service.IOptionService;
import cn.org.alan.exam.util.SecurityUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author WeiJin
 * @since 2024-03-21
 */
@Service
public class ExerciseRecordServiceImpl extends ServiceImpl<ExerciseRecordMapper, ExerciseRecord>
        implements IExerciseRecordService {

    @Resource
    private QuestionMapper questionMapper;
    @Resource
    private QuestionConverter questionConverter;
    @Resource
    private UserExamsScoreMapper userExamsScoreMapper;
    @Resource
    private ExamMapper examMapper;
    @Resource
    private RecordConverter recordConverter;
    @Resource
    private ExamQuestionMapper examQuestionMapper;
    @Resource
    private OptionMapper optionMapper;
    @Resource
    private ExamQuAnswerMapper examQuAnswerMapper;
    @Resource
    private IOptionService optionService;
    @Resource
    private UserExerciseRecordMapper userExerciseRecordMapper;
    @Resource
    private RepoMapper repoMapper;


    @Override
    public Result<List<QuestionSheetVO>> getQuestionSheet(Integer repoId, Integer quType) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .select(Question::getId, Question::getQuType, Question::getRepoId)
                .eq(Question::getRepoId, repoId).eq(quType != null && quType != 0, Question::getQuType, quType);

        List<QuestionSheetVO> list = questionConverter.listEntityToVO(questionMapper.selectList(wrapper));

        return Result.success(null, list);
    }

    @Override
    public Result<IPage<ExamRecordVO>> getExamRecordPage(Integer pageNum, Integer pageSize) {
        // 创建page对象
        Page<Exam> examPage = new Page<>(pageNum, pageSize);
        // 查询该用户已考试的考试id
        LambdaQueryWrapper<UserExamsScore> userExamsScoreWrapper = new LambdaQueryWrapper<>();
        userExamsScoreWrapper.eq(UserExamsScore::getUserId, SecurityUtil.getUserId())
                .select(UserExamsScore::getExamId);
        List<UserExamsScore> userExamsScores = userExamsScoreMapper.selectList(userExamsScoreWrapper);
        List<Integer> examIds = userExamsScores.stream()
                .map(UserExamsScore::getExamId)
                .collect(Collectors.toList());
        // 查询考试表的考试信息
        LambdaQueryWrapper<Exam> examWrapper = new LambdaQueryWrapper<>();
        examWrapper.in(Exam::getId, examIds);
        Page<Exam> examPageResult = examMapper.selectPage(examPage, examWrapper);
        // 实体转换
        Page<ExamRecordVO> examRecordVOPage = recordConverter.pageEntityToVo(examPageResult);
        return Result.success("查询成功", examRecordVOPage);
    }

    @Override
    public Result<List<ExamRecordDetailVO>> getExamRecordDetail(Integer examId) {
        // 1、题干 2、选项 3、自己的答案 4、正确的答案 5、是否正确 6、试题分析
        List<ExamRecordDetailVO> examRecordDetailVOS = new ArrayList<>();
        // 查询该考试的试题
        LambdaQueryWrapper<ExamQuestion> examQuestionWrapper = new LambdaQueryWrapper<>();
        examQuestionWrapper.eq(ExamQuestion::getExamId, examId);
        List<ExamQuestion> examQuestions = examQuestionMapper.selectList(examQuestionWrapper);
        List<Integer> quIds = examQuestions.stream()
                .map(ExamQuestion::getQuestionId)
                .collect(Collectors.toList());
        // 查询题干列表
        List<Question> questions = questionMapper.selectBatchIds(quIds);
        for (Question temp : questions) {
            // 创建返回对象
            ExamRecordDetailVO examRecordDetailVO = new ExamRecordDetailVO();
            // 设置标题
            examRecordDetailVO.setTitle(temp.getContent());
            // 设置分析
            examRecordDetailVO.setAnalyse(temp.getAnalysis());
            // 查询试题选项
            LambdaQueryWrapper<Option> optionWrapper = new LambdaQueryWrapper<>();
            optionWrapper.eq(Option::getQuId, temp.getId());
            List<Option> options = optionMapper.selectList(optionWrapper);
            examRecordDetailVO.setOption(options);
            // 查询试题类型
            LambdaQueryWrapper<Question> QuWrapper = new LambdaQueryWrapper<>();
            QuWrapper.eq(Question::getId, temp.getId());
            Question qu = questionMapper.selectOne(QuWrapper);
            Integer quType = qu.getQuType();
            // 设置正确答案
            LambdaQueryWrapper<Option> opWrapper = new LambdaQueryWrapper<>();
            opWrapper.eq(Option::getQuId, temp.getId());
            List<Option> opList = optionMapper.selectList(opWrapper);
            String current = "";
            ArrayList<Integer> strings = new ArrayList<>();
            for (Option temp1 : options) {
                if (temp1.getIsRight() == 1) {
                    strings.add(temp1.getSort());
                }
            }
            List<String> stringList = strings.stream().map(String::valueOf).collect(Collectors.toList());
            String result = String.join(",", stringList);
            examRecordDetailVO.setRightOption(result);
            // 设置是否正确
            LambdaQueryWrapper<ExamQuAnswer> examQuAnswerWrapper = new LambdaQueryWrapper<>();
            examQuAnswerWrapper.eq(ExamQuAnswer::getUserId, SecurityUtil.getUserId())
                    .eq(ExamQuAnswer::getExamId, examId)
                    .eq(ExamQuAnswer::getQuestionId, temp.getId());
            ExamQuAnswer examQuAnswer = examQuAnswerMapper.selectOne(examQuAnswerWrapper);
            // 如果某题没有作答
            if(examQuAnswer == null){
                examRecordDetailVO.setMyOption(null);
                examRecordDetailVO.setIsRight(-1);
                examRecordDetailVOS.add(examRecordDetailVO);
                continue;
            }
            switch (quType) {
                case 0 -> {
                    // 设置自己的选项
                    LambdaQueryWrapper<Option> optionLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    optionLambdaQueryWrapper.eq(Option::getId,examQuAnswer.getAnswerId());
                    Option op1 = optionMapper.selectOne(optionLambdaQueryWrapper);
                    examRecordDetailVO.setMyOption( Integer.toString(op1.getSort()));
                    // 设置是否正确
                    Option byId = optionService.getById(examQuAnswer.getAnswerId());
                    if (byId.getIsRight() == 1) {
                        examRecordDetailVO.setIsRight(1);
                    } else {
                        examRecordDetailVO.setIsRight(0);
                    }
                }
                case 1 -> {
                    // 将回答id解析为列表
                    String answerId = examQuAnswer.getAnswerId();
                    List<Integer> opIds = Arrays.stream(answerId.split(","))
                            .map(Integer::parseInt)
                            .toList();
                    // 添加选项顺序
                    List<Integer> sorts = new ArrayList<>();
                    for(Integer opId : opIds){
                        LambdaQueryWrapper<Option> optionLambdaQueryWrapper = new LambdaQueryWrapper<>();
                        optionLambdaQueryWrapper.eq(Option::getId,opId);
                        Option option = optionMapper.selectOne(optionLambdaQueryWrapper);
                        sorts.add(option.getSort());
                    }
                    // 设置自己选的选项，选项为顺序 1为A，2为B...
                    List<String> shortList = sorts.stream().map(String::valueOf).collect(Collectors.toList());
                    String myOption = String.join(",", stringList);
                    examRecordDetailVO.setMyOption(myOption);
                    // 查找正确答案
                    LambdaQueryWrapper<Option> optionWrapper1 = new LambdaQueryWrapper<>();
                    optionWrapper1.eq(Option::getIsRight, 1)
                            .eq(Option::getQuId, temp.getId());
                    List<Option> examQuAnswers = optionMapper.selectList(optionWrapper);
                    // 判读啊是否正确
                    examRecordDetailVO.setIsRight(1);
                    for (Option temp1 : examQuAnswers) {
                        boolean containsBanana = opIds.contains(temp.getId());
                        if (containsBanana) {
                            // 只要有一个答案不是正确的则判断为错误
                            examRecordDetailVO.setIsRight(0);
                        }
                    }
                }
                case 2 -> {
                    // 查询自己的的选项
                    LambdaQueryWrapper<Option> optionLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    optionLambdaQueryWrapper.eq(Option::getId,examQuAnswer.getAnswerId());
                    Option op1 = optionMapper.selectOne(optionLambdaQueryWrapper);
                    examRecordDetailVO.setMyOption( Integer.toString(op1.getSort()));
                    // 查询是否正确
                    Option byId = optionService.getById(examQuAnswer.getAnswerId());
                    if (byId.getIsRight() == 1) {
                        examRecordDetailVO.setIsRight(1);
                    } else {
                        examRecordDetailVO.setIsRight(0);
                    }
                }
                case 3 -> {
                    examRecordDetailVO.setMyOption(examQuAnswer.getAnswerContent());
                    if ("1".equals(examQuAnswer.getAnswerId())) {
                        examRecordDetailVO.setIsRight(1);
                    }
                    examRecordDetailVO.setIsRight(0);
                }
                default -> {
                }
            };
            examRecordDetailVOS.add(examRecordDetailVO);
        }
        return Result.success("查询成功",examRecordDetailVOS);
    }

    @Override
    public Result<IPage<ExerciseRecordVO>> getExerciseRecordPage(Integer pageNum, Integer pageSize) {
        // 创建page对象
        Page<Repo> repoPage = new Page<>(pageNum, pageSize);
        // 查询该用户已考试的考试id
        LambdaQueryWrapper<UserExerciseRecord> userExerciseRecordWrapper = new LambdaQueryWrapper<>();
        userExerciseRecordWrapper.eq(UserExerciseRecord::getUserId, SecurityUtil.getUserId());
        List<UserExerciseRecord> userExerciseRecord = userExerciseRecordMapper.selectList(userExerciseRecordWrapper);
        List<Integer> repoIds = userExerciseRecord.stream()
                .map(UserExerciseRecord::getRepoId)
                .collect(Collectors.toList());
        // 查询考试表的考试信息
        LambdaQueryWrapper<Repo> examWrapper = new LambdaQueryWrapper<>();
        examWrapper.in(Repo::getId, repoIds);
        Page<Repo> exercisePageResult = repoMapper.selectPage(repoPage, examWrapper);
        // 实体转换
        Page<ExerciseRecordVO> exerciseRecordVOPage = recordConverter.pageRepoEntityToVo(exercisePageResult);
        return Result.success("查询成功", exerciseRecordVOPage);
    }

    @Override
    public Result<List<ExerciseRecordDetailVO>> getExerciseRecordDetail(Integer exerciseId) {
        // 1、题干 2、选项 3、自己的答案 4、正确的答案 5、是否正确 6、试题分析
        List<ExerciseRecordDetailVO> exerciseRecordDetailVOS = new ArrayList<>();
        // 查询该考试的试题
        LambdaQueryWrapper<UserExerciseRecord> userExerciseRecordWrapper = new LambdaQueryWrapper<>();
        userExerciseRecordWrapper.eq(UserExerciseRecord::getUserId, SecurityUtil.getUserId());
        List<UserExerciseRecord> userExerciseRecord = userExerciseRecordMapper.selectList(userExerciseRecordWrapper);
        List<Integer> quIds = userExerciseRecord.stream()
                .map(UserExerciseRecord::getRepoId)
                .collect(Collectors.toList());
        // 查询题干列表
        LambdaQueryWrapper<Question> questionWrapper = new LambdaQueryWrapper<>();
        questionWrapper.in(Question::getRepoId,quIds);
        List<Question> questions = questionMapper.selectList(questionWrapper);
        for (Question temp : questions) {
            ExerciseRecordDetailVO exerciseRecordDetailVO = new ExerciseRecordDetailVO();
            LambdaQueryWrapper<Option> optionWrapper = new LambdaQueryWrapper<>();
            optionWrapper.eq(Option::getQuId, temp.getId());
            List<Option> options = optionMapper.selectList(optionWrapper);
            // 查询试题类型
            LambdaQueryWrapper<Question> QuWrapper = new LambdaQueryWrapper<>();
            QuWrapper.eq(Question::getId, temp.getId());
            Question qu = questionMapper.selectOne(QuWrapper);
            Integer quType = qu.getQuType();
            // 设置正确答案
            LambdaQueryWrapper<Option> opWrapper = new LambdaQueryWrapper<>();
            opWrapper.eq(Option::getQuId, temp.getId());
            List<Option> opList = optionMapper.selectList(opWrapper);
            String current = "";
            ArrayList<Integer> strings = new ArrayList<>();
            for (Option temp1 : options) {
                if (temp1.getIsRight() == 1) {
                    strings.add(temp1.getSort());
                }
            }
            List<String> stringList = strings.stream().map(String::valueOf).collect(Collectors.toList());
            String result = String.join(",", stringList);
            exerciseRecordDetailVO.setRightOption(result);
            // 设置是否正确
            LambdaQueryWrapper<ExamQuAnswer> examQuAnswerWrapper = new LambdaQueryWrapper<>();
            examQuAnswerWrapper.eq(ExamQuAnswer::getUserId, SecurityUtil.getUserId())
                    .eq(ExamQuAnswer::getExamId, exerciseId)
                    .eq(ExamQuAnswer::getQuestionId, temp.getId());
            ExamQuAnswer examQuAnswer = examQuAnswerMapper.selectOne(examQuAnswerWrapper);
            switch (quType) {
                case 0 -> {
                    LambdaQueryWrapper<Option> optionLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    optionLambdaQueryWrapper.eq(Option::getId,examQuAnswer.getAnswerId());
                    Option op1 = optionMapper.selectOne(optionLambdaQueryWrapper);
                    exerciseRecordDetailVO.setMyOption( Integer.toString(op1.getSort()));
                    Option byId = optionService.getById(examQuAnswer.getAnswerId());
                    if (byId.getIsRight() == 1) {
                        exerciseRecordDetailVO.setIsRight(1);
                    } else {
                        exerciseRecordDetailVO.setIsRight(0);
                    }

                }
                case 1 -> {
                    String answerId = examQuAnswer.getAnswerId();
                    List<Integer> opIds = Arrays.stream(answerId.split(","))
                            .map(Integer::parseInt)
                            .toList();
                    List<Integer> sorts = new ArrayList<>();
                    for(Integer opId : opIds){
                        LambdaQueryWrapper<Option> optionLambdaQueryWrapper = new LambdaQueryWrapper<>();
                        optionLambdaQueryWrapper.eq(Option::getId,opId);
                        Option option = optionMapper.selectOne(optionLambdaQueryWrapper);
                        sorts.add(option.getSort());
                    }
                    List<String> shortList = sorts.stream().map(String::valueOf).collect(Collectors.toList());
                    String myOption = String.join(",", stringList);
                    exerciseRecordDetailVO.setMyOption(myOption);
                    // 查找正确答案
                    LambdaQueryWrapper<Option> optionWrapper1 = new LambdaQueryWrapper<>();
                    optionWrapper1.eq(Option::getIsRight, 1)
                            .eq(Option::getQuId, temp.getId());
                    List<Option> examQuAnswers = optionMapper.selectList(optionWrapper);
                    // 解析用户作答
                    List<Integer> quIds1 = Arrays.stream(examQuAnswer.getAnswerId().split(","))
                            .map(Integer::parseInt)
                            .toList();
                    // 判读啊是否正确
                    for (Option temp1 : examQuAnswers) {
                        boolean containsBanana = quIds1.contains(temp.getId());
                        if (containsBanana) {
                            exerciseRecordDetailVO.setIsRight(0);
                        }
                    }
                    LambdaQueryWrapper<UserBook> userBookWrapper = new LambdaQueryWrapper<>();
                    userBookWrapper.eq(UserBook::getUserId, SecurityUtil.getUserId())
                            .eq(UserBook::getExamId, examQuAnswer.getExamId())
                            .eq(UserBook::getQuId, examQuAnswer.getQuestionId());
                    exerciseRecordDetailVO.setIsRight(1);
                }
                case 2 -> {
                    LambdaQueryWrapper<Option> optionLambdaQueryWrapper = new LambdaQueryWrapper<>();
                    optionLambdaQueryWrapper.eq(Option::getId,examQuAnswer.getAnswerId());
                    Option op1 = optionMapper.selectOne(optionLambdaQueryWrapper);
                    exerciseRecordDetailVO.setMyOption( Integer.toString(op1.getSort()));
                    Option byId = optionService.getById(examQuAnswer.getAnswerId());
                    if (byId.getIsRight() == 1) {
                        LambdaQueryWrapper<UserBook> userBookLambdaQueryWrapper = new LambdaQueryWrapper<>();
                        userBookLambdaQueryWrapper.eq(UserBook::getUserId, SecurityUtil.getUserId())
                                .eq(UserBook::getExamId, examQuAnswer.getExamId())
                                .eq(UserBook::getQuId, examQuAnswer.getQuestionId());
                        exerciseRecordDetailVO.setIsRight(1);
                    } else {
                        exerciseRecordDetailVO.setIsRight(0);
                    }
                }
                case 3 -> {
                    exerciseRecordDetailVO.setMyOption(examQuAnswer.getAnswerContent());
                    if ("1".equals(examQuAnswer.getAnswerId())) {
                        exerciseRecordDetailVO.setIsRight(1);
                    }
                    exerciseRecordDetailVO.setIsRight(0);
                }
                default -> {

                }
            };
            // 设置标题
            exerciseRecordDetailVO.setTitle(temp.getContent());
            // 设置选项
            exerciseRecordDetailVO.setOption(options);
            // 设置分析
            exerciseRecordDetailVO.setAnalyse(temp.getAnalysis());
            exerciseRecordDetailVOS.add(exerciseRecordDetailVO);
        }
        return Result.success("查询成功",exerciseRecordDetailVOS);
    }
}
