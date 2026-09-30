begin;

alter policy pocketpass_api_realtime_read
on realtime.messages
using (
  (
    extension = 'broadcast'
    and (
      private.api_can_access_realtime_topic(realtime.topic())
      or (
        realtime.topic() like 'friend-presence:%'
        and private.api_can_read_presence_topic(realtime.topic())
      )
    )
  )
  or (
    extension = 'presence'
    and private.api_can_read_presence_topic(realtime.topic())
  )
);

commit;
