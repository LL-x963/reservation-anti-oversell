package com.pethome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.BeauticianAddRequest;
import com.pethome.dto.BeauticianPageQuery;
import com.pethome.dto.BeauticianUpdateRequest;
import com.pethome.entity.Beautician;
import com.pethome.mapper.BeauticianMapper;
import com.pethome.service.BeauticianService;
import com.pethome.vo.BeauticianVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 美容师服务实现
 *
 * @author pet-home
 */
@Slf4j
@Service
public class BeauticianServiceImpl extends ServiceImpl<BeauticianMapper, Beautician> implements BeauticianService {

    /** 在职 */
    private static final int STATUS_ON_DUTY = 1;
    /** 默认状态：在职 */
    private static final int DEFAULT_STATUS = STATUS_ON_DUTY;
    /** 新增美容师默认评分 */
    private static final BigDecimal DEFAULT_SCORE = new BigDecimal("5.00");
    /** 技能标签存储字段最大长度，与 skill_tags VARCHAR(255) 对齐 */
    private static final int SKILL_TAGS_MAX_LENGTH = 255;
    /** 技能标签分隔符 */
    private static final String SKILL_TAG_SEPARATOR = ",";

    @Override
    public IPage<BeauticianVO> pageForAdmin(BeauticianPageQuery query) {
        LambdaQueryWrapper<Beautician> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(query.getSkillTag())) {
            // 技能标签逗号分隔存储，用 FIND_IN_SET 精确匹配单个标签，避免 LIKE 误命中
            wrapper.apply("FIND_IN_SET({0}, skill_tags)", query.getSkillTag().trim());
        }
        wrapper.like(StringUtils.isNotBlank(query.getName()), Beautician::getName, query.getName())
                .eq(query.getStatus() != null, Beautician::getStatus, query.getStatus())
                .orderByDesc(Beautician::getId);

        Page<Beautician> page = page(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return page.convert(this::toVO);
    }

    @Override
    public List<BeauticianVO> listOnDuty() {
        return lambdaQuery()
                .eq(Beautician::getStatus, STATUS_ON_DUTY)
                .orderByAsc(Beautician::getId)
                .list()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(BeauticianAddRequest request) {
        Beautician entity = new Beautician();
        entity.setName(request.getName().trim());
        entity.setAvatar(request.getAvatar());
        entity.setSkillTags(joinSkillTags(request.getSkillTags()));
        entity.setServiceRange(request.getServiceRange());
        entity.setEntryTime(request.getEntryTime());
        entity.setStatus(request.getStatus() == null ? DEFAULT_STATUS : request.getStatus());
        // 评分由评价体系维护，新增统一初始化
        entity.setScore(DEFAULT_SCORE);
        save(entity);

        log.info("新增美容师成功: id={}, name={}, score={}", entity.getId(), entity.getName(), DEFAULT_SCORE);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(BeauticianUpdateRequest request) {
        if (getById(request.getId()) == null) {
            throw new BusinessException("美容师不存在");
        }

        Beautician entity = new Beautician();
        entity.setId(request.getId());
        entity.setName(request.getName().trim());
        entity.setAvatar(request.getAvatar());
        entity.setSkillTags(joinSkillTags(request.getSkillTags()));
        entity.setServiceRange(request.getServiceRange());
        entity.setEntryTime(request.getEntryTime());
        entity.setStatus(request.getStatus());
        // score 不参与更新（null 字段不会被更新），由评价体系维护
        updateById(entity);

        log.info("修改美容师成功: id={}, name={}", request.getId(), entity.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("美容师不存在");
        }

        long unfinishedOrders = baseMapper.countUnfinishedOrderByBeauticianId(id);
        if (unfinishedOrders > 0) {
            throw new BusinessException("该美容师存在 " + unfinishedOrders + " 个未完成订单，暂不能删除");
        }

        removeById(id);
        log.info("删除美容师成功: id={}", id);
    }

    /**
     * 技能标签规范化：去 null、去首尾空格、去空白项、去重并保持顺序
     */
    private List<String> normalizeSkillTags(List<String> skillTags) {
        if (skillTags == null || skillTags.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(skillTags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /**
     * 技能标签拼接为逗号分隔字符串
     *
     * <p>无有效标签时返回空串而非 null：MyBatis-Plus 默认忽略 null 字段的更新，
     * 存空串才能保证「清空技能标签」在修改时真正落库。</p>
     */
    private String joinSkillTags(List<String> skillTags) {
        List<String> normalized = normalizeSkillTags(skillTags);
        if (normalized.isEmpty()) {
            return StringUtils.EMPTY;
        }
        String joined = String.join(SKILL_TAG_SEPARATOR, normalized);
        if (joined.length() > SKILL_TAGS_MAX_LENGTH) {
            throw new BusinessException("技能标签总长度不能超过" + SKILL_TAGS_MAX_LENGTH);
        }
        return joined;
    }

    /**
     * 逗号分隔字符串拆分为技能标签数组
     */
    private List<String> splitSkillTags(String storedSkillTags) {
        if (StringUtils.isBlank(storedSkillTags)) {
            return Collections.emptyList();
        }
        return Arrays.stream(storedSkillTags.split(SKILL_TAG_SEPARATOR))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private BeauticianVO toVO(Beautician entity) {
        BeauticianVO vo = new BeauticianVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setAvatar(entity.getAvatar());
        vo.setSkillTags(splitSkillTags(entity.getSkillTags()));
        vo.setServiceRange(entity.getServiceRange());
        vo.setEntryTime(entity.getEntryTime());
        vo.setScore(entity.getScore());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

}
