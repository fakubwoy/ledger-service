-- W3C trace context of the request that wrote the row, so the async Kafka publish joins the same trace.
alter table outbox add column traceparent text;
