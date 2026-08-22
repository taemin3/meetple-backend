package com.meetple.backend.domain.legal.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum LegalDocumentType {
    SERVICE_TERMS(LegalDocumentAction.ACCEPTED),
    PRIVACY_POLICY(LegalDocumentAction.ACKNOWLEDGED),
    AGE_14_CONFIRMATION(LegalDocumentAction.CONFIRMED);

    private final LegalDocumentAction action;
}
