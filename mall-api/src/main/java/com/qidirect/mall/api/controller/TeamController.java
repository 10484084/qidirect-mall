package com.qidirect.mall.api.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.domain.vo.TeamMemberVO;
import com.qidirect.mall.infra.security.SecurityUtils;
import com.qidirect.mall.infra.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 门店端团队树：按层懒加载，仅返回自己或自己子树下的直接下级。
 */
@RestController
@RequestMapping("/team")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping
    public ApiResponse<List<TeamMemberVO>> children(
            @RequestParam(required = false) Long parentId) {
        return ApiResponse.ok(teamService.children(SecurityUtils.currentStoreId(), parentId));
    }
}
