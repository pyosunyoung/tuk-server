package com.bungae.presence.dto;

import com.bungae.global.common.Activity;
import com.bungae.presence.domain.Presence;

/** 내 콜 상태. available=false 면 나머지는 null. remainingSeconds = 남은 TTL(초). */
public record MyPresenceResponse(boolean available, Activity activity, Long remainingSeconds) {

    public static MyPresenceResponse of(Presence presence) {
        return new MyPresenceResponse(true, presence.getActivity(), presence.getTtlSeconds());
    }

    public static MyPresenceResponse off() {
        return new MyPresenceResponse(false, null, null);
    }
}
