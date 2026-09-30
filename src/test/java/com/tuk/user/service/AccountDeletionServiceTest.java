package com.tuk.user.service;

import com.tuk.flash.domain.Flash;
import com.tuk.flash.domain.FlashRecipient;
import com.tuk.flash.domain.MeetupRoom;
import com.tuk.flash.repository.FlashRecipientRepository;
import com.tuk.flash.repository.FlashRepository;
import com.tuk.flash.repository.MeetupRoomRepository;
import com.tuk.friendship.domain.Friendship;
import com.tuk.friendship.repository.FriendshipRepository;
import com.tuk.global.common.Activity;
import com.tuk.notification.domain.DeviceToken;
import com.tuk.notification.repository.DeviceTokenRepository;
import com.tuk.user.domain.User;
import com.tuk.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탈퇴는 외래키 순서가 곧 정합성이라 실제 DB 로 돌린다(모킹하면 순서 실수를 못 잡는다).
 * 테스트 트랜잭션 안에서 돌고 끝나면 롤백된다.
 */
@SpringBootTest
@Transactional
class AccountDeletionServiceTest {

    @Autowired AccountDeletionService service;
    @Autowired UserRepository userRepository;
    @Autowired FriendshipRepository friendshipRepository;
    @Autowired FlashRepository flashRepository;
    @Autowired FlashRecipientRepository recipientRepository;
    @Autowired MeetupRoomRepository roomRepository;
    @Autowired DeviceTokenRepository deviceTokenRepository;
    @Autowired EntityManager em;

    @Test
    @DisplayName("탈퇴: 내 것은 지우고, 남의 모임방은 남기되 나를 가리키는 흔적만 뺀다")
    void deletesOwnDataAndDetachesFromOthers() {
        User me = user("me");
        User friend = user("friend");
        User other = user("other");

        Friendship accepted = Friendship.builder().requester(me).addressee(friend).build();
        accepted.accept();
        friendshipRepository.save(accepted);
        friendshipRepository.save(Friendship.builder().requester(other).addressee(me).build()); // 받은 신청
        Friendship untouched = Friendship.builder().requester(friend).addressee(other).build();
        untouched.accept();
        friendshipRepository.save(untouched);

        // 내가 띄운 툭 — friend 가 합류, friend 가 장소를 정함
        Flash mine = flash(me);
        MeetupRoom mineRoom = room(mine);
        mineRoom.setPlace("홍대입구역", null, null, null, null, friend);
        recipient(mine, friend, true);

        // friend 가 띄운 툭 — 내가 합류해 장소·시간을 정함, other 는 초대만
        Flash theirs = flash(friend);
        MeetupRoom theirsRoom = room(theirs);
        theirsRoom.setPlace("합정역", null, null, null, null, me);
        theirsRoom.setMeetAt(LocalDateTime.now().plusHours(2), me);
        recipient(theirs, me, true);
        recipient(theirs, other, false);

        deviceTokenRepository.save(DeviceToken.builder().user(me).token("tok-me")
                .platform(DeviceToken.Platform.ANDROID).build());
        deviceTokenRepository.save(DeviceToken.builder().user(friend).token("tok-friend")
                .platform(DeviceToken.Platform.ANDROID).build());

        Long mineId = mine.getId(), theirsId = theirs.getId();
        em.flush();
        em.clear();

        service.delete(me.getId());
        em.flush(); // 외래키 위반이 있으면 여기서 터진다
        em.clear();

        // 나
        assertThat(userRepository.existsById(me.getId())).isFalse();
        assertThat(deviceTokenRepository.findByUserId(me.getId())).isEmpty();

        // 내가 띄운 툭은 모임방·초대와 함께 사라진다
        assertThat(flashRepository.existsById(mineId)).isFalse();
        assertThat(roomRepository.findByFlashId(mineId)).isEmpty();
        assertThat(recipientRepository.findByFlashIdIn(java.util.List.of(mineId))).isEmpty();

        // 남의 툭은 남는다 — 나만 빠지고, 장소·시간은 그대로, "정한 사람"만 비워진다
        assertThat(flashRepository.existsById(theirsId)).isTrue();
        MeetupRoom kept = roomRepository.findByFlashId(theirsId).orElseThrow();
        assertThat(kept.getPlaceName()).isEqualTo("합정역");
        assertThat(kept.getMeetAt()).isNotNull();
        assertThat(kept.getPlaceSetBy()).isNull();
        assertThat(kept.getMeetAtSetBy()).isNull();
        assertThat(recipientRepository.findByFlashIdIn(java.util.List.of(theirsId)))
                .extracting(r -> r.getUser().getNickname())
                .containsExactly("other");

        // 친구 관계: 나와 얽힌 것만 사라진다
        assertThat(friendshipRepository.findAcceptedFriendIds(friend.getId())).containsExactly(other.getId());
        assertThat(friendshipRepository.findPendingSent(other.getId())).isEmpty();

        // 남의 데이터는 그대로
        assertThat(userRepository.existsById(friend.getId())).isTrue();
        assertThat(deviceTokenRepository.findByUserId(friend.getId())).hasSize(1);
    }

    private User user(String nickname) {
        return userRepository.save(User.builder()
                .nickname(nickname).provider("dev").providerId("del-" + nickname + "-" + System.nanoTime())
                .build());
    }

    private Flash flash(User sender) {
        return flashRepository.save(Flash.builder()
                .sender(sender).activity(Activity.MEAL).expiresAt(LocalDateTime.now().plusHours(1)).build());
    }

    private MeetupRoom room(Flash flash) {
        return roomRepository.save(MeetupRoom.builder().flash(flash).expiresAt(flash.getExpiresAt()).build());
    }

    private void recipient(Flash flash, User user, boolean joined) {
        FlashRecipient r = FlashRecipient.builder().flash(flash).user(user).build();
        if (joined) r.join();
        recipientRepository.save(r);
    }
}
