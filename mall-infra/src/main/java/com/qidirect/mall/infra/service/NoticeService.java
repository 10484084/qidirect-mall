package com.qidirect.mall.infra.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qidirect.mall.common.api.PageResult;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.domain.entity.Notice;
import com.qidirect.mall.domain.entity.Store;
import com.qidirect.mall.domain.vo.NoticeVO;
import com.qidirect.mall.infra.mapper.NoticeMapper;
import com.qidirect.mall.infra.mapper.StoreMapper;
import com.qidirect.mall.infra.sms.SmsGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 站内消息与短信：业务事件写入 notice，门店消息同时按模板发送短信（失败不影响业务）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    public static final int TARGET_STORE = 1;
    public static final int TARGET_ADMIN = 2;
    public static final String SMS_ORDER_CREATED = "ORDER_CREATED";
    public static final String SMS_SHIP = "SHIP";
    public static final String SMS_WITHDRAW_RESULT = "WITHDRAW_RESULT";
    public static final String SMS_WITHDRAW_FAIL = "WITHDRAW_FAIL";
    public static final String SMS_JOIN_SUCCESS = "JOIN_SUCCESS";
    public static final String SMS_BONUS_CREDITED = "BONUS_CREDITED";
    public static final String SMS_PAY_ABNORMAL = "PAY_ABNORMAL";

    private static final long MAX_PAGE_SIZE = 100L;

    private final NoticeMapper noticeMapper;
    private final StoreMapper storeMapper;
    private final SmsGateway smsGateway;

    public void notifyStore(Long storeId, String title, String content,
                            String bizType, String bizNo, String smsTemplateCode) {
        insert(TARGET_STORE, storeId, title, content, bizType, bizNo);
        if (StrUtil.isBlank(smsTemplateCode)) {
            return;
        }
        Store store = storeMapper.selectById(storeId);
        if (store == null || StrUtil.isBlank(store.getPhone())) {
            return;
        }
        sendSms(store.getPhone(), smsTemplateCode, title, content);
    }

    public void notifyAdmins(String title, String content, String bizType, String bizNo) {
        insert(TARGET_ADMIN, 0L, title, content, bizType, bizNo);
    }

    public PageResult<NoticeVO> storePage(Long storeId, long page, long size) {
        long current = Math.max(page, 1);
        long pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<Notice> result = noticeMapper.selectPage(new Page<>(current, pageSize),
                Wrappers.<Notice>lambdaQuery()
                        .eq(Notice::getTargetType, TARGET_STORE)
                        .eq(Notice::getTargetId, storeId)
                        .orderByDesc(Notice::getId));
        List<NoticeVO> records = result.getRecords().stream()
                .map(item -> new NoticeVO(item.getId(), item.getTitle(), item.getContent(),
                        StrUtil.nullToEmpty(item.getBizType()), StrUtil.nullToEmpty(item.getBizNo()),
                        item.getReaded(), item.getCreatedAt()))
                .toList();
        return PageResult.of(records, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public long unreadCount(Long storeId) {
        Long count = noticeMapper.selectCount(Wrappers.<Notice>lambdaQuery()
                .eq(Notice::getTargetType, TARGET_STORE)
                .eq(Notice::getTargetId, storeId)
                .eq(Notice::getReaded, 0));
        return count == null ? 0L : count;
    }

    public void markRead(Long storeId, Long noticeId) {
        int updated = noticeMapper.update(null, Wrappers.<Notice>lambdaUpdate()
                .eq(Notice::getId, noticeId)
                .eq(Notice::getTargetType, TARGET_STORE)
                .eq(Notice::getTargetId, storeId)
                .set(Notice::getReaded, 1));
        if (updated == 0) {
            throw new BizException(ErrorCode.NOT_FOUND.getCode(), "消息不存在");
        }
    }

    private void insert(int targetType, Long targetId, String title, String content,
                        String bizType, String bizNo) {
        Notice notice = new Notice();
        notice.setTargetType(targetType);
        notice.setTargetId(targetId);
        notice.setTitle(StrUtil.maxLength(StrUtil.nullToEmpty(title), 60));
        notice.setContent(StrUtil.maxLength(StrUtil.nullToEmpty(content), 500));
        notice.setBizType(StrUtil.nullToEmpty(bizType));
        notice.setBizNo(StrUtil.nullToEmpty(bizNo));
        notice.setReaded(0);
        noticeMapper.insert(notice);
    }

    private void sendSms(String phone, String templateCode, String title, String content) {
        try {
            smsGateway.send(phone, templateCode, Map.of(
                    "title", StrUtil.nullToEmpty(title),
                    "content", StrUtil.maxLength(StrUtil.nullToEmpty(content), 100)));
        } catch (Exception e) {
            log.warn("短信发送失败：phone={} template={} error={}", phone, templateCode, e.toString());
        }
    }
}
