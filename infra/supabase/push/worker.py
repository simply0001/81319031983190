import json
import logging
import os
import time
from datetime import timedelta
from urllib.request import Request, urlopen


def rpc(name, params):
    base = os.environ.get("POSTGREST_URL", "http://rest:3000").rstrip("/")
    request = Request(
        f"{base}/rpc/{name}",
        data=json.dumps(params).encode(),
        headers={
            "Authorization": f"Bearer {os.environ['SERVICE_ROLE_KEY']}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    with urlopen(request, timeout=15) as response:
        body = response.read()
        return json.loads(body) if body else None


def deliver(job, messaging):
    ios = job.get("platform", "android") == "ios"
    options = {}
    data = job["data"]
    board = data.get("type") == "board"
    thread = (data.get("thread_id") or data.get("board_id")) if board else data.get("conversation_id")
    if ios:
        data = {**data, "title": "PocketPass", "body": "There is new activity in your boards." if board else "You have a new message."}
        options["apns"] = messaging.APNSConfig(
            headers={
                "apns-push-type": "alert",
                "apns-priority": "10",
                "apns-expiration": str(int(time.time()) + 900),
                "apns-collapse-id": thread,
            },
            payload=messaging.APNSPayload(aps=messaging.Aps(
                alert=messaging.ApsAlert(title=data["title"], body=data["body"]),
                sound="default",
                thread_id=thread,
            )),
        )
    else:
        options["android"] = messaging.AndroidConfig(
            priority="high",
            ttl=timedelta(minutes=15),
            restricted_package_name="com.pocketpass.app",
        )
    message = messaging.Message(
        token=job["token"],
        data=data,
        **options,
    )
    try:
        messaging.send(message)
        return "sent"
    except messaging.UnregisteredError:
        return "unregistered"
    except Exception as error:
        logging.warning("Firebase delivery will retry (%s)", type(error).__name__)
        return "retry"


def process_batch(call, messaging):
    jobs = call("claim_message_push_batch", {})
    for job in jobs:
        outcome = deliver(job, messaging)
        call("finish_message_push", {
            "p_id": job["id"], "p_lease_id": job["lease_id"], "p_outcome": outcome,
        })
    if jobs:
        logging.info("Processed %d message notification deliveries", len(jobs))
    return len(jobs)


def process_board_batch(call, messaging):
    jobs = call("claim_board_push_batch", {})
    for job in jobs:
        outcome = deliver(job, messaging)
        call("finish_board_push", {
            "p_id": job["id"], "p_lease_id": job["lease_id"], "p_outcome": outcome,
        })
    return len(jobs)


def main():
    import firebase_admin
    from firebase_admin import credentials, messaging

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    firebase_admin.initialize_app(
        credentials.Certificate(os.environ["GOOGLE_APPLICATION_CREDENTIALS"]),
        options={"httpTimeout": 10},
    )
    if not os.environ.get("SERVICE_ROLE_KEY"):
        raise RuntimeError("SERVICE_ROLE_KEY is required")
    logging.info("PocketPass message push worker started")
    while True:
        try:
            count = process_batch(rpc, messaging) + process_board_batch(rpc, messaging)
            if count < 10:
                time.sleep(2)
        except Exception as error:
            logging.error("Message push worker will retry (%s)", type(error).__name__)
            time.sleep(15)


if __name__ == "__main__":
    main()
