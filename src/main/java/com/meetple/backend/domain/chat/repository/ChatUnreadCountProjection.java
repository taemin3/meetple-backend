package com.meetple.backend.domain.chat.repository;

public interface ChatUnreadCountProjection {

    Long getMeetingId();

    Long getUnreadCount();
}
