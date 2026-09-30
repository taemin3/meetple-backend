package com.meetple.backend.domain.ai;

import static com.meetple.backend.domain.ai.AiSearchContracts.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AiMeetingSearchRepository {
    private static final int VECTOR_CANDIDATE_LIMIT = 200;
    private static final RowMapper<Candidate> CANDIDATE_MAPPER = (rs, index) -> new Candidate(
            rs.getLong("id"), rs.getString("title"), rs.getString("content"),
            rs.getString("category_name"), rs.getString("location_name"),
            rs.getObject("meeting_date", LocalDateTime.class), rs.getObject("end_date", LocalDateTime.class),
            rs.getInt("max_people"), rs.getInt("current_people"), rs.getDouble("distance_meters")
    );
    private final NamedParameterJdbcTemplate jdbc;

    public AiMeetingSearchRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<String> categories() {
        return jdbc.queryForList("select name from categories order by name", Map.of(), String.class);
    }

    public Candidates search(long memberId, Filters filters) {
        return search(memberId, filters, null, null);
    }

    public Candidates search(long memberId, Filters filters, List<Double> queryEmbedding, String embeddingModel) {
        Map<String, Object> params = parameters(memberId, filters);
        boolean hybrid = queryEmbedding != null;
        if (hybrid != (embeddingModel != null)) {
            throw new IllegalArgumentException("질문 임베딩과 모델은 함께 전달해야 합니다.");
        }
        String sql = "";
        if (hybrid) {
            params.put("queryEmbedding", vectorLiteral(queryEmbedding));
            params.put("embeddingModel", embeddingModel);
            sql = """
                    with semantic_candidates as materialized (
                        select me.meeting_id,
                               me.embedding <=> cast(:queryEmbedding as vector) as semantic_distance
                        from meeting_embeddings me
                        where me.embedding_model = :embeddingModel
                        order by me.embedding <=> cast(:queryEmbedding as vector)
                        limit %d
                    )
                    """.formatted(VECTOR_CANDIDATE_LIMIT);
        }
        sql += """
                select m.id, m.title, m.content, c.name as category_name, m.location_name,
                       m.meeting_date, m.end_date, m.max_people, m.current_people,
                       ST_Distance(m.location, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography), false) as distance_meters
                """;
        if (hybrid) {
            sql += """
                       , (case when lower(m.title) like :keyword escape '!'
                                    or lower(m.content) like :keyword escape '!'
                               then 0.45 else 0 end
                          + case when sc.semantic_distance is null then 0
                                 else 0.55 * (1 - sc.semantic_distance) end
                         ) as hybrid_score
                    """;
        }
        sql += """
                from meetings m
                join categories c on c.id = m.category_id
                """;
        if (hybrid) {
            sql += " left join semantic_candidates sc on sc.meeting_id = m.id\n";
        }
        sql += """
                where m.deleted_at is null and m.status = 'RECRUITING'
                  and m.current_people < m.max_people
                  and m.meeting_date >= :startsAt and m.meeting_date < :endsBefore
                  and exists (select 1 from members viewer where viewer.id = :memberId and viewer.deleted_at is null)
                  and not exists (select 1 from member_blocks b
                      where b.blocker_member_id = :memberId and b.blocked_member_id = m.host_id)
                  and ST_DWithin(m.location, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography), :radiusMeters, false)
                """;
        if (filters.category() != null && !filters.category().isBlank()) sql += " and c.name = :category\n";
        if (filters.keyword() != null && !filters.keyword().isBlank()) {
            if (hybrid) {
                sql += " and (lower(m.title) like :keyword escape '!'"
                        + " or lower(m.content) like :keyword escape '!' or sc.meeting_id is not null)\n";
            } else {
                sql += " and (lower(m.title) like :keyword escape '!' or lower(m.content) like :keyword escape '!')\n";
            }
        } else if (hybrid) {
            sql += " and sc.meeting_id is not null\n";
        }
        if (filters.startsAtTime() != null && filters.endsBeforeTime() != null) {
            if (filters.startsAtTime().isBefore(filters.endsBeforeTime())) {
                sql += " and cast(m.meeting_date as time) >= :startsAtTime"
                        + " and cast(m.meeting_date as time) < :endsBeforeTime\n";
            } else {
                sql += " and (cast(m.meeting_date as time) >= :startsAtTime"
                        + " or cast(m.meeting_date as time) < :endsBeforeTime)\n";
            }
        } else if (filters.startsAtTime() != null) {
            sql += " and cast(m.meeting_date as time) >= :startsAtTime\n";
        } else if (filters.endsBeforeTime() != null) {
            sql += " and cast(m.meeting_date as time) < :endsBeforeTime\n";
        }
        sql += hybrid
                ? " order by hybrid_score desc, distance_meters, m.meeting_date, m.id limit 21"
                : " order by distance_meters, m.meeting_date, m.id limit 21";
        List<Candidate> rows = jdbc.query(sql, params, CANDIDATE_MAPPER);
        return new Candidates(rows.stream().limit(20).toList(), rows.size() > 20);
    }

    public List<Candidate> findEligibleByIds(long memberId, Filters filters, List<Long> meetingIds) {
        if (meetingIds.isEmpty()) return List.of();
        Map<String, Object> params = parameters(memberId, filters);
        params.put("meetingIds", meetingIds);
        String sql = """
                select m.id, m.title, m.content, c.name as category_name, m.location_name,
                       m.meeting_date, m.end_date, m.max_people, m.current_people,
                       ST_Distance(m.location, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography), false) as distance_meters
                from meetings m
                join categories c on c.id = m.category_id
                where m.id in (:meetingIds)
                  and m.deleted_at is null and m.status = 'RECRUITING'
                  and m.current_people < m.max_people
                  and m.meeting_date >= :startsAt and m.meeting_date < :endsBefore
                  and exists (select 1 from members viewer where viewer.id = :memberId and viewer.deleted_at is null)
                  and not exists (select 1 from member_blocks b
                      where b.blocker_member_id = :memberId and b.blocked_member_id = m.host_id)
                  and ST_DWithin(m.location, CAST(ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326) AS geography), :radiusMeters, false)
                """;
        sql = appendStructuredFilters(sql, filters);
        return jdbc.query(sql + " order by m.id", params, CANDIDATE_MAPPER);
    }

    private String vectorLiteral(List<Double> embedding) {
        return embedding.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private Map<String, Object> parameters(long memberId, Filters f) {
        Map<String, Object> params = new HashMap<>();
        params.put("memberId", memberId);
        params.put("latitude", f.latitude()); params.put("longitude", f.longitude());
        params.put("radiusMeters", f.radiusMeters()); params.put("startsAt", f.startsAt());
        params.put("endsBefore", f.endsBefore()); params.put("category", f.category());
        params.put("startsAtTime", f.startsAtTime()); params.put("endsBeforeTime", f.endsBeforeTime());
        String literal = f.keyword() == null ? "" : f.keyword().strip().toLowerCase(java.util.Locale.ROOT);
        params.put("keyword", "%" + literal.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        return params;
    }

    private String appendStructuredFilters(String sql, Filters filters) {
        if (filters.category() != null && !filters.category().isBlank()) sql += " and c.name = :category\n";
        if (filters.startsAtTime() != null && filters.endsBeforeTime() != null) {
            if (filters.startsAtTime().isBefore(filters.endsBeforeTime())) {
                sql += " and cast(m.meeting_date as time) >= :startsAtTime"
                        + " and cast(m.meeting_date as time) < :endsBeforeTime\n";
            } else {
                sql += " and (cast(m.meeting_date as time) >= :startsAtTime"
                        + " or cast(m.meeting_date as time) < :endsBeforeTime)\n";
            }
        } else if (filters.startsAtTime() != null) {
            sql += " and cast(m.meeting_date as time) >= :startsAtTime\n";
        } else if (filters.endsBeforeTime() != null) {
            sql += " and cast(m.meeting_date as time) < :endsBeforeTime\n";
        }
        return sql;
    }
}
