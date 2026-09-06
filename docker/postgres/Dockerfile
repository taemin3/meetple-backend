FROM postgis/postgis:16-3.4

ARG PG_BIGM_VERSION=1.2-20250903

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       ca-certificates \
       curl \
       make \
       gcc \
       clang-13 \
       llvm-13 \
       postgresql-server-dev-16 \
    && curl -L \
       https://github.com/pgbigm/pg_bigm/archive/refs/tags/v${PG_BIGM_VERSION}.tar.gz \
       -o /tmp/pg_bigm.tar.gz \
    && tar -xzf /tmp/pg_bigm.tar.gz -C /tmp \
    && cd /tmp/pg_bigm-${PG_BIGM_VERSION} \
    && make USE_PGXS=1 PG_CONFIG=/usr/bin/pg_config \
    && make USE_PGXS=1 PG_CONFIG=/usr/bin/pg_config install \
    && rm -rf /tmp/pg_bigm* \
    && apt-get purge -y --auto-remove \
       curl \
       make \
       gcc \
       clang-13 \
       llvm-13 \
       postgresql-server-dev-16 \
    && rm -rf /var/lib/apt/lists/*