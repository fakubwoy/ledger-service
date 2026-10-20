create table accounts (
    id         uuid primary key,
    owner      varchar(255) not null,
    currency   varchar(3)   not null check (currency ~ '^[A-Z]{3}$'),
    created_at timestamptz  not null
);
