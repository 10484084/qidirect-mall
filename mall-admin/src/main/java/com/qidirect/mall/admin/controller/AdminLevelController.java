package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.dto.admin.LevelSaveRequest;
import com.qidirect.mall.domain.vo.StoreLevelVO;
import com.qidirect.mall.infra.service.StoreLevelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台档位管理。
 */
@RestController
@RequestMapping("/levels")
@RequiredArgsConstructor
public class AdminLevelController {

    private final StoreLevelService storeLevelService;

    @GetMapping
    public ApiResponse<List<StoreLevelVO>> list() {
        return ApiResponse.ok(storeLevelService.list());
    }

    @PostMapping
    public ApiResponse<StoreLevelVO> create(@Valid @RequestBody LevelSaveRequest request) {
        return ApiResponse.ok(storeLevelService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StoreLevelVO> update(@PathVariable Long id,
                                            @Valid @RequestBody LevelSaveRequest request) {
        return ApiResponse.ok(storeLevelService.update(id, request));
    }
}
