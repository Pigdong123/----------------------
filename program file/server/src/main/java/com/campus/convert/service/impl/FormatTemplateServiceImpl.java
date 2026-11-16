package com.campus.convert.service.impl;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.dto.TemplateRequest;
import com.campus.convert.domain.entity.FormatTemplate;
import com.campus.convert.mapper.FormatTemplateMapper;
import com.campus.convert.service.FormatTemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 格式模板服务实现
 */
@Service
@RequiredArgsConstructor
public class FormatTemplateServiceImpl implements FormatTemplateService {

    private final FormatTemplateMapper formatTemplateMapper;
    private final ObjectMapper objectMapper;

    @Override
    public List<FormatTemplate> listAll() {
        return formatTemplateMapper.selectList(null);
    }

    @Override
    public List<FormatTemplate> listBySourceExt(String sourceExt) {
        return formatTemplateMapper.selectBySourceExt(normalize(sourceExt));
    }

    @Override
    public FormatTemplate resolve(String sourceExt, String targetExt) {
        return formatTemplateMapper.selectBySourceExtAndTarget(normalize(sourceExt), normalize(targetExt));
    }

    @Override
    public FormatTemplate create(TemplateRequest request) {
        String sourceExt = normalize(request.getSourceExt());
        String targetExt = normalize(request.getTargetExt());
        if (formatTemplateMapper.selectBySourceExtAndTarget(sourceExt, targetExt) != null) {
            throw new BizException(ResultCode.BAD_REQUEST, "该格式组合模板已存在");
        }
        FormatTemplate template = new FormatTemplate();
        apply(template, request);
        template.setCreatedAt(LocalDateTime.now());
        formatTemplateMapper.insert(template);
        return template;
    }

    @Override
    public FormatTemplate update(Long id, TemplateRequest request) {
        FormatTemplate template = requireTemplate(id);
        apply(template, request);
        formatTemplateMapper.updateById(template);
        return template;
    }

    @Override
    public void delete(Long id) {
        formatTemplateMapper.deleteById(id);
    }

    @Override
    public void toggle(Long id, boolean enabled) {
        FormatTemplate template = requireTemplate(id);
        template.setEnabled(enabled ? 1 : 0);
        formatTemplateMapper.updateById(template);
    }

    private void apply(FormatTemplate template, TemplateRequest request) {
        template.setSourceExt(normalize(request.getSourceExt()));
        template.setTargetExt(normalize(request.getTargetExt()));
        template.setEngine(request.getEngine().toUpperCase());
        template.setParamsSchema(toJson(request.getParamsSchema()));
        template.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
    }

    private FormatTemplate requireTemplate(Long id) {
        FormatTemplate template = formatTemplateMapper.selectById(id);
        if (template == null) {
            throw new BizException(ResultCode.NOT_FOUND, "格式模板不存在");
        }
        return template;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "参数 Schema 无法序列化");
        }
    }

    private String normalize(String ext) {
        return ext == null ? null : ext.trim().toLowerCase().replace(".", "");
    }
}
