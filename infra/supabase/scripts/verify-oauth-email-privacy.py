#!/usr/bin/env python3
"""Read-only email disclosure check using two explicitly consented test-account sessions."""

import argparse
import base64
import json
import os
from pathlib import Path
import stat
from urllib.parse import urlparse
from urllib.request import Request, urlopen


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"FAIL  {message}")


def load_tokens(path: Path) -> dict:
    if os.name == "posix":
        require(stat.S_IMODE(path.stat().st_mode) & 0o077 == 0,
                f"{path.name} must not be readable by group or others")
    with path.open(encoding="utf-8") as source:
        value = json.load(source)
    require(isinstance(value, dict), f"{path.name} is not a token response")
    require(isinstance(value.get("access_token"), str), f"{path.name} lacks an access token")
    require(isinstance(value.get("id_token"), str), f"{path.name} lacks an ID token")
    return value


def claims(token: str) -> dict:
    parts = token.split(".")
    require(len(parts) == 3, "a supplied token is not a JWT")
    payload = parts[1] + "=" * (-len(parts[1]) % 4)
    value = json.loads(base64.urlsafe_b64decode(payload))
    require(isinstance(value, dict), "a JWT payload is not an object")
    return value


def request_json(url: str, token: str, body: bytes | None = None) -> dict:
    headers = {"Authorization": f"Bearer {token}"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    request = Request(url, data=body, headers=headers, method="POST" if body is not None else "GET")
    with urlopen(request, timeout=15) as response:
        value = json.load(response)
    require(isinstance(value, dict), "an API response is not an object")
    return value


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--openid-only", type=Path, required=True,
                        help="OAuth token response for scope=openid")
    parser.add_argument("--email-scope", type=Path, required=True,
                        help="OAuth token response for scope=openid email")
    parser.add_argument("--api-base", default="https://api.pocketpass.xyz")
    args = parser.parse_args()

    base = args.api_base.rstrip("/")
    parsed = urlparse(base)
    require(parsed.scheme == "https" and parsed.netloc and not parsed.username
            and not parsed.password and not parsed.path and not parsed.query
            and not parsed.fragment, "API base must be an HTTPS origin")

    plain = load_tokens(args.openid_only)
    scoped = load_tokens(args.email_scope)
    plain_access = claims(plain["access_token"])
    scoped_access = claims(scoped["access_token"])
    plain_id = claims(plain["id_token"])
    scoped_id = claims(scoped["id_token"])

    require(plain_access.get("sub") == scoped_access.get("sub"),
            "both consent runs must use the same test account")
    require(plain_id.get("sub") == scoped_id.get("sub") == plain_access.get("sub"),
            "ID tokens must belong to the same test account as the access tokens")
    require(plain_access.get("role") == scoped_access.get("role") == "api_client",
            "connected-app access tokens must use the restricted role")
    for access in (plain_access, scoped_access):
        require(not access.get("email"), "a connected-app access token exposes email")
        require(not access.get("user_metadata"), "an access token exposes user metadata")
    require("email" not in str(plain_access.get("scope", "")).split(),
            "the openid-only token unexpectedly carries email scope")
    require("email" in str(scoped_access.get("scope", "")).split(),
            "the consented token does not carry email scope")

    plain_info = request_json(f"{base}/auth/v1/oauth/userinfo", plain["access_token"])
    scoped_info = request_json(f"{base}/auth/v1/oauth/userinfo", scoped["access_token"])
    for label, response in (("openid-only ID token", plain_id),
                            ("openid-only UserInfo", plain_info)):
        require(response.get("sub") == plain_access.get("sub"),
                f"{label} belongs to a different account")
        require(not response.get("email") and "email_verified" not in response,
                f"{label} exposes email without consent")
    require(scoped_info.get("sub") == plain_access.get("sub"),
            "consented UserInfo belongs to a different account")
    require(bool(scoped_id.get("email")) and scoped_id.get("email") == scoped_info.get("email"),
            "consented email is missing or inconsistent between ID token and UserInfo")

    for label, token in (("openid-only", plain["access_token"]),
                         ("email-scoped", scoped["access_token"])):
        for endpoint, body in (("me.get", b"{}"),
                               ("profiles.get", json.dumps({"user_id": plain_access["sub"]}).encode())):
            response = request_json(f"{base}/v1/{endpoint}", token, body)
            profile = response.get("profile")
            require(isinstance(profile, dict) and "email" not in profile,
                    f"{label} {endpoint} exposes email or failed")

    print("PASS  email is absent without consent and visible only through consented OIDC claims")
    print("PASS  connected-app access tokens and ordinary developer profiles omit email")


if __name__ == "__main__":
    main()
