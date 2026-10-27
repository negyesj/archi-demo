-- Műveleti trail: a kézbesítés a kiváltó művelet trace-éhez kapcsolódik, és mérhető az esemény keletkezésétől.
alter table delivery add column trace_id varchar(64);
alter table delivery add column parent_span_id varchar(32);
alter table delivery add column event_occurred_at timestamptz;
