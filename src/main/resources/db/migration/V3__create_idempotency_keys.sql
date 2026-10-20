-- The primary key makes concurrent requests with the same key serialize on insert.
create table idempotency_keys (
    key          varchar(255) primary key,
    request_hash varchar(64)  not null,
    response     text,
    created_at   timestamptz  not null
);
