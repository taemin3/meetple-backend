package com.meetple.backend.global.observability;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;

import java.util.Set;

final class CloudWatchApplicationMetricFilter implements MeterFilter {

    private static final Set<String> ALLOWED_METERS = Set.of(
            "process.cpu.usage",
            "jvm.gc.pause",
            "tomcat.threads.busy",
            "tomcat.threads.current",
            "tomcat.threads.config.max",
            "hikaricp.connections.active",
            "hikaricp.connections.idle",
            "hikaricp.connections.pending",
            "hikaricp.connections.max",
            "lettuce.command.completion"
    );

    private static final String HTTP_REQUEST_METER = "http.server.requests";

    private static final Set<String> ALLOWED_HTTP_URIS = Set.of(
            "/api/v1/performance/auth-probe",
            "/api/v1/categories",
            "/api/v1/meetings",
            "/api/v1/meetings/summaries",
            "/api/v1/meetings/{meetingId}",
            "/api/v1/users/me"
    );

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        if (ALLOWED_METERS.contains(id.getName())) {
            return MeterFilterReply.NEUTRAL;
        }
        if (HTTP_REQUEST_METER.equals(id.getName()) && ALLOWED_HTTP_URIS.contains(id.getTag("uri"))) {
            return MeterFilterReply.NEUTRAL;
        }
        return MeterFilterReply.DENY;
    }
}
