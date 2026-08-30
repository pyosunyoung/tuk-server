package com.tuk.global.auth.social;

import com.tuk.global.exception.AuthException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** provider 에 맞는 SocialClient 로 위임. 등록된 SocialClient 빈들을 자동 수집한다. */
@Component
public class SocialClientRouter {

    private final Map<OAuthProvider, SocialClient> clients;

    public SocialClientRouter(List<SocialClient> clientList) {
        this.clients = clientList.stream()
                .collect(Collectors.toMap(SocialClient::provider, Function.identity()));
    }

    public SocialUserInfo fetch(OAuthProvider provider, String socialAccessToken) {
        SocialClient client = clients.get(provider);
        if (client == null) {
            throw new AuthException("아직 지원하지 않는 소셜 로그인 제공자입니다: " + provider.key());
        }
        return client.fetch(socialAccessToken);
    }
}
