package com.tuk.presence.dto;

import com.tuk.global.common.Activity;
import com.tuk.presence.domain.Presence;

/** 내 콜 상태. available=false 면 나머지는 null. remainingSeconds = 남은 TTL(초). */
public record MyPresenceResponse(boolean available, Activity activity, Long remainingSeconds) {

    public static MyPresenceResponse of(Presence presence) {
        return new MyPresenceResponse(true, presence.getActivity(), presence.getTtlSeconds());
    }

    public static MyPresenceResponse off() {
        return new MyPresenceResponse(false, null, null);
    }
}
