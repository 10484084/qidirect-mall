package com.qidirect.mall.infra.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.qidirect.mall.domain.entity.JoinOrder;
import com.qidirect.mall.domain.entity.OrderInfo;
import com.qidirect.mall.domain.entity.PerformanceRecord;
import com.qidirect.mall.domain.entity.RechargeOrder;
import com.qidirect.mall.domain.entity.Withdraw;
import com.qidirect.mall.domain.vo.AdminDashboardVO;
import com.qidirect.mall.infra.mapper.JoinOrderMapper;
import com.qidirect.mall.infra.mapper.OrderInfoMapper;
import com.qidirect.mall.infra.mapper.PerformanceRecordMapper;
import com.qidirect.mall.infra.mapper.RechargeOrderMapper;
import com.qidirect.mall.infra.mapper.WithdrawMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

/**
 * 后台工作台统计：今日/本月加盟、预存、订货与待处理事项。
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final int JOIN_PAID = 1;
    private static final int JOIN_TYPE_NEW = 1;
    private static final int ORDER_STATUS_PENDING_SHIP = 1;
    private static final int ABNORMAL = 3;

    private final JoinOrderMapper joinOrderMapper;
    private final RechargeOrderMapper rechargeOrderMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final PerformanceRecordMapper performanceRecordMapper;
    private final WithdrawMapper withdrawMapper;

    public AdminDashboardVO summary() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        long[] todayJoin = joinStat(todayStart);
        long[] monthJoin = joinStat(monthStart);
        long[] todayRecharge = rechargeStat(todayStart);
        long[] monthRecharge = rechargeStat(monthStart);
        long[] todayOrder = orderStat(todayStart);
        long[] monthOrder = orderStat(monthStart);

        long monthPerformance = sum(performanceRecordMapper.selectMaps(new QueryWrapper<PerformanceRecord>()
                .select("IFNULL(SUM(amount),0) AS total")
                .eq("month", YearMonth.now().toString())));

        Long pendingShip = orderInfoMapper.selectCount(Wrappers.<OrderInfo>lambdaQuery()
                .eq(OrderInfo::getStatus, ORDER_STATUS_PENDING_SHIP));
        Long pendingWithdraw = withdrawMapper.selectCount(Wrappers.<Withdraw>lambdaQuery()
                .in(Withdraw::getStatus, 0, 1));
        Long pendingAbnormal = joinOrderMapper.selectCount(Wrappers.<JoinOrder>lambdaQuery()
                .eq(JoinOrder::getStatus, ABNORMAL));
        Long rechargeAbnormal = rechargeOrderMapper.selectCount(Wrappers.<RechargeOrder>lambdaQuery()
                .eq(RechargeOrder::getStatus, ABNORMAL));

        return new AdminDashboardVO(
                todayJoin[0], todayJoin[1], monthJoin[0], monthJoin[1],
                todayRecharge[0], todayRecharge[1], monthRecharge[0], monthRecharge[1],
                todayOrder[0], todayOrder[1], monthOrder[0], monthOrder[1],
                monthPerformance,
                pendingShip == null ? 0L : pendingShip,
                pendingWithdraw == null ? 0L : pendingWithdraw,
                (pendingAbnormal == null ? 0L : pendingAbnormal)
                        + (rechargeAbnormal == null ? 0L : rechargeAbnormal));
    }

    /**
     * 加盟单统计：仅首次加盟（type=1）且已支付。
     */
    private long[] joinStat(LocalDateTime start) {
        LambdaQueryWrapper<JoinOrder> countQuery = Wrappers.<JoinOrder>lambdaQuery()
                .eq(JoinOrder::getStatus, JOIN_PAID)
                .eq(JoinOrder::getType, JOIN_TYPE_NEW)
                .ge(JoinOrder::getPayTime, start);
        Long count = joinOrderMapper.selectCount(countQuery);
        long amount = sum(joinOrderMapper.selectMaps(new QueryWrapper<JoinOrder>()
                .select("IFNULL(SUM(amount),0) AS total")
                .eq("status", JOIN_PAID)
                .eq("type", JOIN_TYPE_NEW)
                .ge("pay_time", start)));
        return new long[]{count == null ? 0L : count, amount};
    }

    private long[] rechargeStat(LocalDateTime start) {
        Long count = rechargeOrderMapper.selectCount(Wrappers.<RechargeOrder>lambdaQuery()
                .eq(RechargeOrder::getStatus, JOIN_PAID)
                .ge(RechargeOrder::getPayTime, start));
        long amount = sum(rechargeOrderMapper.selectMaps(new QueryWrapper<RechargeOrder>()
                .select("IFNULL(SUM(amount),0) AS total")
                .eq("status", JOIN_PAID)
                .ge("pay_time", start)));
        return new long[]{count == null ? 0L : count, amount};
    }

    private long[] orderStat(LocalDateTime start) {
        Long count = orderInfoMapper.selectCount(Wrappers.<OrderInfo>lambdaQuery()
                .in(OrderInfo::getStatus, 1, 2, 3)
                .ge(OrderInfo::getPayTime, start));
        long amount = sum(orderInfoMapper.selectMaps(new QueryWrapper<OrderInfo>()
                .select("IFNULL(SUM(total_amount),0) AS total")
                .in("status", 1, 2, 3)
                .ge("pay_time", start)));
        return new long[]{count == null ? 0L : count, amount};
    }

    private long sum(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Object total = rows.get(0) == null ? null : rows.get(0).get("total");
        if (total == null) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(total));
    }
}
