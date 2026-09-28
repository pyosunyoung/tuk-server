package com.tuk.flash.dto;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.flash.domain.MeetupLifecycle;
import com.tuk.global.common.Activity;
import com.tuk.user.domain.User;

import java.time.LocalDateTime;

/**
 * 내가 받은 툭 1건. myStatus = PENDING(아직 합류 안 함) / JOINED.
 * expiresAt = 합류 마감, meetAt = 약속 시간(합류한 뒤 누군가 정했으면 값이 있다).
 * visibleUntil = 목록에서 치울 시각. 앱은 이 값만 보고 지운다(규칙은 MeetupLifecycle).
 */
public record ReceivedFlashResponse(
        Long flashId,
        Long senderId,
        String senderNickname,
        String senderProfileImage,
        Activity activity,
        String message,
        LocalDateTime scheduledAt,
        LocalDateTime expiresAt,
        LocalDateTime meetAt,
        LocalDateTime visibleUntil,
        String myStatus
) {
    public static ReceivedFlashResponse from(FlashRecipient recipient, LocalDateTime meetAt) {
        Flash flash = recipient.getFlash();
        User sender = flash.getSender();
        boolean joined = recipient.getStatus() == FlashRecipient.RecipientStatus.JOINED;
        return new ReceivedFlashResponse(
                flash.getId(),
                sender.getId(),
                sender.getNickname(),
                sender.getProfileImage(),
                flash.getActivity(),
                flash.getMessage(),
                flash.getScheduledAt(),
                flash.getExpiresAt(),
                meetAt,
                MeetupLifecycle.visibleUntil(flash.getExpiresAt(), meetAt, joined),
                recipient.getStatus().name());
    }
}
