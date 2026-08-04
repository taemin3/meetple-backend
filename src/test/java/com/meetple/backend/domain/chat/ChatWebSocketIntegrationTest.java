package com.meetple.backend.domain.chat;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.auth.repository.AccessTokenBlacklistRepository;
import com.meetple.backend.domain.auth.repository.RefreshTokenRepository;
import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.chat.repository.ChatMessageRepository;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.security.JwtTokenProvider;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ChatWebSocketIntegrationTest {

    private static final String SESSION_ID = "chat-websocket-integration";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipationRepository participationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @MockitoBean
    private AccessTokenBlacklistRepository accessTokenBlacklistRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private Member participant;
    private Meeting meeting;

    @BeforeEach
    void setUp() {
        given(accessTokenBlacklistRepository.exists(anyString())).willReturn(false);
        given(refreshTokenRepository.existsByMemberIdAndSessionId(anyLong(), anyString()))
                .willReturn(true);

        String suffix = UUID.randomUUID().toString();
        Member host = memberRepository.save(member("host-" + suffix));
        participant = memberRepository.save(member("participant-" + suffix));
        Category category = categoryRepository.save(Category.create("category-" + suffix));
        meeting = meetingRepository.save(meeting(host, category));
        MeetingParticipation participation = MeetingParticipation.apply(
                meeting,
                participant,
                "함께 참여하고 싶습니다."
        );
        participation.approve();
        participationRepository.saveAndFlush(participation);

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    }

    @AfterEach
    void tearDown() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void approvedParticipantSendsPersistsAndReceivesMessage() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(participant, SESSION_ID);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        stompSession = stompClient.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                }
        ).get(5, SECONDS);

        CompletableFuture<String> receivedFrame = new CompletableFuture<>();
        stompSession.subscribe(
                "/topic/chat/rooms/" + meeting.getId(),
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return byte[].class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        receivedFrame.complete(
                                new String((byte[]) payload, StandardCharsets.UTF_8)
                        );
                    }
                }
        );

        UUID clientMessageId = UUID.randomUUID();
        String content = "  통합 테스트 메시지\n둘째 줄  ";
        StompHeaders sendHeaders = new StompHeaders();
        sendHeaders.setDestination("/app/chat/rooms/" + meeting.getId() + "/messages");
        sendHeaders.setContentType(MediaType.APPLICATION_JSON);
        stompSession.send(
                sendHeaders,
                objectMapper.writeValueAsBytes(new SendFrame(clientMessageId, content))
        );

        JsonNode response = objectMapper.readTree(receivedFrame.get(5, SECONDS));
        assertThat(response.path("success").asBoolean()).isTrue();
        assertThat(response.path("data").path("roomId").asLong()).isEqualTo(meeting.getId());
        assertThat(response.path("data").path("clientMessageId").asText())
                .isEqualTo(clientMessageId.toString());
        assertThat(response.path("data").path("content").asText()).isEqualTo(content);

        assertThat(messageRepository.findByMeetingIdAndSenderIdAndClientMessageId(
                meeting.getId(),
                participant.getId(),
                clientMessageId
        )).get().satisfies(message -> {
            assertThat(message.getContent()).isEqualTo(content);
            assertThat(message.getRoomSequence()).isEqualTo(1L);
        });
    }

    private Member member(String nickname) {
        return Member.createUser(
                nickname + "@meetple.com",
                "encoded-password",
                nickname,
                "Seoul"
        );
    }

    private Meeting meeting(Member host, Category category) {
        return Meeting.create(
                host,
                category,
                "WebSocket integration meeting",
                "Verify the full chat contract.",
                "Yeouido Park",
                "Seoul",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500"),
                10,
                LocalDateTime.now().plusDays(1),
                null
        );
    }

    private record SendFrame(UUID clientMessageId, String content) {
    }
}
