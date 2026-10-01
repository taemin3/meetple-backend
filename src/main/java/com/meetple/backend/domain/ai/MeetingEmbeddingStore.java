package com.meetple.backend.domain.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MeetingEmbeddingStore {
    private final NamedParameterJdbcTemplate jdbc;

    public int upsertIfCurrent(
            MeetingEmbeddingDocument document,
            String embeddingModel,
            List<Double> embedding
    ) {
        Map<String, Object> params = new HashMap<>();
        params.put("meetingId", document.meetingId());
        params.put("title", document.title());
        params.put("category", document.category());
        params.put("locationName", document.locationName());
        params.put("address", document.address());
        params.put("description", document.description());
        params.put("embedding", vectorLiteral(embedding));
        params.put("embeddingModel", embeddingModel);
        params.put("contentHash", document.contentHash());
        return jdbc.update("""
                insert into meeting_embeddings as stored
                    (meeting_id, embedding, embedding_model, content_hash, embedded_at)
                select m.id, cast(:embedding as vector), :embeddingModel, :contentHash, current_timestamp
                from meetings m
                join categories c on c.id = m.category_id
                where m.id = :meetingId
                  and m.deleted_at is null
                  and m.title = :title
                  and m.content = :description
                  and m.location_name = :locationName
                  and m.address = :address
                  and c.name = :category
                on conflict (meeting_id) do update
                set embedding = excluded.embedding,
                    embedding_model = excluded.embedding_model,
                    content_hash = excluded.content_hash,
                    embedded_at = excluded.embedded_at
                where exists (
                    select 1
                    from meetings current_meeting
                    join categories current_category on current_category.id = current_meeting.category_id
                    where current_meeting.id = excluded.meeting_id
                      and current_meeting.deleted_at is null
                      and current_meeting.title = :title
                      and current_meeting.content = :description
                      and current_meeting.location_name = :locationName
                      and current_meeting.address = :address
                      and current_category.name = :category
                )
                """, params);
    }

    private String vectorLiteral(List<Double> embedding) {
        return embedding.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }
}
