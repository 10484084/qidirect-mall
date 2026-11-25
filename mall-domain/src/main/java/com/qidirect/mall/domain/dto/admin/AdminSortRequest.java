package com.qidirect.mall.domain.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 拖拽排序：按传入顺序重排，序号从 1 依次递增。
 */
public record AdminSortRequest(@NotEmpty List<Long> ids) {
}
