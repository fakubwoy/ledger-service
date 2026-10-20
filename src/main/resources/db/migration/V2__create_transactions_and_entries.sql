create table transactions (
    id         uuid primary key,
    type       varchar(16) not null check (type in ('TRANSFER', 'DEPOSIT')),
    created_at timestamptz not null
);

-- Signed amounts in minor units: debit is negative, credit is positive.
-- The entries of one transaction always sum to zero.
create table entries (
    id             uuid primary key,
    transaction_id uuid   not null references transactions (id),
    account_id     uuid   not null references accounts (id),
    amount         bigint not null check (amount <> 0)
);

create index entries_account_id_idx on entries (account_id);
create index entries_transaction_id_idx on entries (transaction_id);
