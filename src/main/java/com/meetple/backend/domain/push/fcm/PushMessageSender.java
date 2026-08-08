package com.meetple.backend.domain.push.fcm;

import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.List;

public interface PushMessageSender {

    PushSendResult send(PushMessage message, List<PushDeviceTarget> targets);
}
