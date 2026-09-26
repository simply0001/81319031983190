begin;

set local search_path = public, extensions;

select extensions.plan(4);

select extensions.ok(
  private.can_access_realtime_topic('app_updates', '98000000-0000-4000-8000-000000000021'),
  'any signed-in user may subscribe to the app_updates topic'
);

select extensions.ok(
  not private.can_access_realtime_topic('app_updates:extra', '98000000-0000-4000-8000-000000000021'),
  'only the exact app_updates topic is public'
);

select extensions.ok(
  not private.can_access_realtime_topic(
    'tokens:98000000-0000-4000-8000-000000000022',
    '98000000-0000-4000-8000-000000000021'
  ),
  'per-user topics stay private to their owner'
);

select extensions.lives_ok(
  $$
    select realtime.send(
      '{"versionCode":4,"minSupportedVersionCode":4}'::jsonb,
      'app_update',
      'app_updates',
      true
    )
  $$,
  'the poller can broadcast a manifest change on app_updates'
);

select * from extensions.finish();

rollback;
