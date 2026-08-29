CREATE TABLE IF NOT EXISTS public.debezium_heartbeat (
    id SMALLINT PRIMARY KEY,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_debezium_heartbeat_singleton CHECK (id = 1)
);

INSERT INTO public.debezium_heartbeat (id, updated_at)
SELECT 1, CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM public.debezium_heartbeat
    WHERE id = 1
);
