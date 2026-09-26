begin;

-- A pair's receipts for one UTC day all fold into that day's encounter, but
-- the two sides do not always upload the same handshake: when the last
-- fragment of an exchange is lost, one side records handshake X and the
-- other, minutes later, handshake Y. With one receipt per reporter per
-- encounter the first upload of the day was final, a later receipt for the
-- handshake the peer actually recorded was dropped, and the pair could never
-- confirm that day. Key receipts by handshake transcript instead; the
-- confirmation join already matches any low/high pair sharing a transcript.
alter table private.nearby_receipts
  drop constraint nearby_receipts_pkey;

alter table private.nearby_receipts
  add constraint nearby_receipts_pkey primary key (encounter_id, reporter_id, transcript_hash);

comment on table private.nearby_receipts is 'One row per reporter per handshake transcript for an encounter; the same reporter may record several handshakes on one day and the encounter confirms as soon as both sides share one transcript.';

commit;
