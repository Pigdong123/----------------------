package com.campus.convert.service;

import com.campus.convert.domain.dto.TemplateRequest;
import com.campus.convert.domain.entity.FormatTemplate;

import java.util.List;

/**
 * 格式模板服务（管理端）：允许的源/目标格式、引擎、参数 Schema
 */
public interface FormatTemplateService {

    List<FormatTemplate> listAll();

    List<FormatTemplate> listBySourceExt(String sourceExt);

    FormatTemplate resolve(String sourceExt, String targetExt);

    FormatTemplate create(TemplateRequest request);

    FormatTemplate update(Long id, TemplateRequest request);

    void delete(Long id);

    void toggle(Long id, boolean enabled);
}
