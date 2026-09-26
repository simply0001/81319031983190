import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

from worker import deliver, process_batch, process_board_batch


class WorkerTests(unittest.TestCase):
    def setUp(self):
        class UnregisteredError(Exception):
            pass
        self.messaging = SimpleNamespace(
            Message=Mock(side_effect=lambda **kw: kw), AndroidConfig=Mock(side_effect=lambda **kw: kw),
            send=Mock(), UnregisteredError=UnregisteredError,
            APNSConfig=Mock(side_effect=lambda **kw: kw), APNSPayload=Mock(side_effect=lambda **kw: kw),
            Aps=Mock(side_effect=lambda **kw: kw), ApsAlert=Mock(side_effect=lambda **kw: kw),
        )
        self.job = {"id": "job", "lease_id": "lease", "token": "private-token", "data": {"type": "message"}}

    def test_high_priority_data_only_payload(self):
        self.assertEqual("sent", deliver(self.job, self.messaging))
        message = self.messaging.send.call_args.args[0]
        self.assertNotIn("notification", message)
        self.assertEqual("high", message["android"]["priority"])
        self.assertEqual(900, message["android"]["ttl"].total_seconds())
        self.assertEqual("com.pocketpass.app", message["android"]["restricted_package_name"])

    def test_unregistered_devices_removed(self):
        self.messaging.send.side_effect = self.messaging.UnregisteredError()
        self.assertEqual("unregistered", deliver(self.job, self.messaging))

    def test_ios_alert_has_expiry_grouping_and_no_message_preview(self):
        self.job.update(platform="ios", data={"type": "message", "conversation_id": "conversation",
                                             "title": "Private sender", "body": "Private message"})
        with patch("worker.time.time", return_value=1000):
            self.assertEqual("sent", deliver(self.job, self.messaging))
        message = self.messaging.send.call_args.args[0]
        self.assertNotIn("android", message)
        self.assertEqual("1900", message["apns"]["headers"]["apns-expiration"])
        self.assertEqual("alert", message["apns"]["headers"]["apns-push-type"])
        self.assertEqual("conversation", message["apns"]["headers"]["apns-collapse-id"])
        self.assertEqual("conversation", message["apns"]["payload"]["aps"]["thread_id"])
        self.assertEqual("You have a new message.", message["apns"]["payload"]["aps"]["alert"]["body"])
        self.assertNotIn("Private", str(message))
        self.assertEqual("Private message", self.job["data"]["body"])

    def test_temporary_or_configuration_failure_retried_without_disabling_device(self):
        self.messaging.send.side_effect = RuntimeError("private details")
        with self.assertLogs(level="WARNING") as logs:
            self.assertEqual("retry", deliver(self.job, self.messaging))
        self.assertNotIn("private details", str(logs.output))
        self.assertNotIn("private-token", str(logs.output))

    def test_acks_exact_lease(self):
        call = Mock(side_effect=[[self.job], None])
        self.assertEqual(1, process_batch(call, self.messaging))
        self.assertEqual(("finish_message_push", {"p_id": "job", "p_lease_id": "lease", "p_outcome": "sent"}), call.call_args.args)

    def test_failed_ack_keeps_job_for_redelivery(self):
        call = Mock(side_effect=[[self.job], OSError("offline")])
        with self.assertRaises(OSError):
            process_batch(call, self.messaging)

    def test_empty_queue_never_calls_firebase(self):
        self.assertEqual(0, process_batch(Mock(return_value=[]), self.messaging))
        self.messaging.send.assert_not_called()

    def test_board_ios_alert_groups_by_thread_and_never_displays_board_content(self):
        self.job.update(platform="ios", data={"type": "board", "board_id": "board", "thread_id": "thread",
                                             "title": "Private board", "body": "Spoiler text"})
        self.assertEqual("sent", deliver(self.job, self.messaging))
        message = self.messaging.send.call_args.args[0]
        self.assertEqual("thread", message["apns"]["headers"]["apns-collapse-id"])
        self.assertEqual("There is new activity in your boards.", message["apns"]["payload"]["aps"]["alert"]["body"])
        self.assertNotIn("Spoiler", str(message))

    def test_board_queue_acks_its_own_lease(self):
        self.job["data"] = {"type": "board", "board_id": "board", "thread_id": "thread"}
        call = Mock(side_effect=[[self.job], None])
        self.assertEqual(1, process_board_batch(call, self.messaging))
        self.assertEqual(("finish_board_push", {"p_id": "job", "p_lease_id": "lease", "p_outcome": "sent"}), call.call_args.args)


if __name__ == "__main__":
    unittest.main()
