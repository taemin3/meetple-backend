FROM postgis/postgis:16-3.5-alpine@sha256:47e961a569fd52ff31f0fe205ed91eeab17d9f5fff6722e6d7ea6b588748b293

ARG PG_BIGM_VERSION=1.2-20250903

RUN apk add --no-cache --virtual .pg-bigm-build-deps \
       build-base \
       clang21 \
       curl \
       icu-dev \
       llvm21 \
    && curl --fail --location --retry 3 \
       https://github.com/pgbigm/pg_bigm/archive/refs/tags/v${PG_BIGM_VERSION}.tar.gz \
       -o /tmp/pg_bigm.tar.gz \
    && tar -xzf /tmp/pg_bigm.tar.gz -C /tmp \
    && cd /tmp/pg_bigm-${PG_BIGM_VERSION} \
    && make USE_PGXS=1 PG_CONFIG=/usr/local/bin/pg_config \
    && make USE_PGXS=1 PG_CONFIG=/usr/local/bin/pg_config install \
    && rm -rf /tmp/pg_bigm* \
    && apk del .pg-bigm-build-deps
