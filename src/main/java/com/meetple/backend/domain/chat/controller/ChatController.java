package com.meetple.backend.domain.chat.controller;

import com.meetple.backend.domain.chat.dto.request.MarkChatRoomReadRequest;
import com.meetple.backend.domain.chat.dto.response.ChatMessagePageResponse;
import com.meetple.backend.domain.chat.dto.response.ChatReadStateResponse;
import com.meetple.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.meetple.backend.domain.chat.service.ChatService;
import com.meetple.backend.global.config.OpenApiConfig;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.PageResponse;
import com.meetple.backend.global.response.SuccessStatus;
import com.meetple.backend.global.security.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Chat", description = "모임 채팅 조회 및 읽음 상태 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat/rooms")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH_SCHEME)
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    @Operation(summary = "내 채팅방 목록 조회")
    public ResponseEntity<ApiResponse<PageResponse<ChatRoomSummaryResponse>>> getRooms(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                chatService.getRooms(authenticatedMember.id(), PageRequest.of(page, size))
        );
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "채팅방 단건 조회")
    public ResponseEntity<ApiResponse<ChatRoomSummaryResponse>> getRoom(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long roomId
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                chatService.getRoom(authenticatedMember.id(), roomId)
        );
    }

    @GetMapping("/{roomId}/messages")
    @Operation(summary = "채팅 메시지 커서 조회")
    public ResponseEntity<ApiResponse<ChatMessagePageResponse>> getMessages(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long beforeSequence,
            @RequestParam(required = false) Long afterSequence,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                chatService.getMessages(
                        authenticatedMember.id(),
                        roomId,
                        beforeSequence,
                        afterSequence,
                        size
                )
        );
    }

    @PatchMapping("/{roomId}/read")
    @Operation(summary = "채팅방 마지막 읽은 메시지 갱신")
    public ResponseEntity<ApiResponse<ChatReadStateResponse>> markRead(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long roomId,
            @Valid @RequestBody MarkChatRoomReadRequest request
    ) {
        return ApiResponse.success(
                SuccessStatus.OK,
                chatService.markRead(authenticatedMember.id(), roomId, request)
        );
    }
}
