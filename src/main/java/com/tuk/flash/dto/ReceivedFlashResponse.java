package com.tuk.flash.dto;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.global.common.Activity;
import com.tuk.user.domain.User;

import java.time.LocalDateTime;

/**
 * 내가 받은 툭 1건. myStatus = PENDING(아직 합류 안 함) / JOINED.
 * expiresAt = 합류 마감, meetAt = 약속 시간(합류한 뒤 누군가 정했으면 값이 있다).
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
        String myStatus
) {
    public static ReceivedFlashResponse from(FlashRecipient recipient, LocalDateTime meetAt) {
        Flash flash = recipient.getFlash();
        User sender = flash.getSender();
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
                recipient.getStatus().name());
    }
}
