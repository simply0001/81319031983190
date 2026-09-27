begin;

drop function public.record_interaction_event(uuid, uuid, public.interaction_event_type, uuid, jsonb, timestamptz);
drop function public.save_profile_mii(uuid, bigint, integer, jsonb, text, text);
drop function public.publish_system_notification(text, text, uuid);

notify pgrst, 'reload schema';
commit;
