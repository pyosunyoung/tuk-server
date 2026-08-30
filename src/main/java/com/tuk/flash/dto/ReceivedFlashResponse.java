package com.tuk.flash.dto;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.global.common.Activity;
import com.tuk.user.domain.User;

import java.time.LocalDateTime;

/** 내가 받은 활성 번개 1건. myStatus = PENDING(아직 합류 안 함) / JOINED. */
public record ReceivedFlashResponse(
        Long flashId,
        Long senderId,
        String senderNickname,
        String senderProfileImage,
        Activity activity,
        String message,
        LocalDateTime scheduledAt,
        LocalDateTime expiresAt,
        String myStatus
) {
    public static ReceivedFlashResponse from(FlashRecipient recipient) {
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
                recipient.getStatus().name());
    }
}
