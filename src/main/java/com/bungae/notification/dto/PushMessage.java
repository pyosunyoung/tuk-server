package com.bungae.notification.dto;

import java.util.Map;

/** 푸시 1건. data 는 앱이 딥링크 등에 쓰는 키-값(예: type, flashId). */
public record PushMessage(String title, String body, Map<String, String> data) {
}
