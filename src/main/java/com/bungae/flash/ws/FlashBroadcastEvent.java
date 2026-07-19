package com.bungae.flash.ws;

import com.bungae.flash.dto.FlashPushEvent;

import java.util.List;

/**
 * 트랜잭션 안에서 발행되는 내부 이벤트. 리스너가 커밋 이후 targetUserIds 중 접속자에게 payload 를 전파한다.
 * payload 는 트랜잭션 안(세션 열림)에서 미리 완성해 담는다 → 커밋 후 lazy 로딩 없음.
 */
public record FlashBroadcastEvent(FlashPushEvent payload, List<Long> targetUserIds) {
}
