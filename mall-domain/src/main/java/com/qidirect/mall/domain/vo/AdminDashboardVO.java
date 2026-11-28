package com.qidirect.mall.domain.vo;

/**
 * 后台工作台概览：今日/本月开通、预存、订货数据与待处理事项。
 */
public record AdminDashboardVO(
        long todayJoinCount,
        long todayJoinAmount,
        long monthJoinCount,
        long monthJoinAmount,
        long todayRechargeCount,
        long todayRechargeAmount,
        long monthRechargeCount,
        long monthRechargeAmount,
        long todayOrderCount,
        long todayOrderAmount,
        long monthOrderCount,
        long monthOrderAmount,
        long monthPerformanceAmount,
        long pendingShipCount,
        long pendingWithdrawCount,
        long pendingAbnormalCount) {
}
