-- Events written in the same transaction as the entries; a poller publishes them to Kafka.
create table outbox (
    id           bigserial primary key,
    aggregate_id uuid        not null,
    payload      text        not null,
    created_at   timestamptz not null,
    published_at timestamptz
);

create index outbox_unpublished_idx on outbox (id) where published_at is null;

-- Read model built by the statement consumer from TransferCompleted events.
create table statement_lines (
    account_id             uuid        not null,
    transaction_id         uuid        not null,
    type                   varchar(16) not null,
    counterparty_account_id uuid       not null,
    amount                 bigint      not null,
    currency               varchar(3)  not null,
    occurred_at            timestamptz not null,
    -- Makes the consumer idempotent: a redelivered event inserts nothing.
    primary key (account_id, transaction_id)
);
