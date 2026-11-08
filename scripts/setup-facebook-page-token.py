#!/usr/bin/env python3
"""One-time Meta Page token setup. Tokens are prompted privately and never printed."""
import argparse
import getpass
import json
import os
from pathlib import Path
import re
import tempfile
import urllib.error
import urllib.parse
import urllib.request


class SetupError(Exception):
    pass


def request(version, path, values, bearer=None):
    # Form POST keeps app secrets and the exchange token out of URL/query logs.
    url = f"https://graph.facebook.com/{version}/{path}"
    headers = {"Authorization": f"Bearer {bearer}"} if bearer else {}
    data = None
    if bearer:
        url += "?" + urllib.parse.urlencode(values)
    else:
        data = urllib.parse.urlencode(values).encode()
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers), timeout=30) as response:
            result = json.load(response)
        if not isinstance(result, dict) or "error" in result:
            raise SetupError("Meta rejected the request. Check authorization, Page access and app permissions.")
        return result
    except (urllib.error.URLError, ValueError):
        # Remote bodies/URLs may contain credentials: never echo exceptions.
        raise SetupError("Meta request failed. Check connectivity, authorization and app permissions.") from None


def save_token(path, token):
    if not isinstance(token, str) or not token or not re.fullmatch(r"[A-Za-z0-9_.~-]+", token):
        raise SetupError("Meta returned an invalid Page token; configuration was not changed.")
    path = Path(path)
    if path.is_symlink():
        raise SetupError("Refusing to replace a symbolic link.")
    existing = path.read_text() if path.exists() else ""
    replacement = f"FACEBOOK_PAGE_TOKEN='{token}'"
    lines = existing.splitlines()
    updated = []
    found = False
    for line in lines:
        if re.match(r"^\s*(?:export\s+)?FACEBOOK_PAGE_TOKEN\s*=", line):
            if not found:
                updated.append(replacement)
            found = True
        else:
            updated.append(line)
    if not found:
        updated.append(replacement)
    fd, temporary = tempfile.mkstemp(prefix=".facebook-token-", dir=path.parent)
    try:
        with os.fdopen(fd, "w") as output:
            output.write("\n".join(updated) + "\n")
        os.replace(temporary, path)  # mkstemp creates a private 0600 file.
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def provision(version, app_id, app_secret, user_token, page_id):
    exchange = request(version, "oauth/access_token", {
        "grant_type": "fb_exchange_token", "client_id": app_id,
        "client_secret": app_secret, "fb_exchange_token": user_token,
    })
    long_lived = exchange.get("access_token")
    if not isinstance(long_lived, str) or not long_lived:
        raise SetupError("Meta did not return a long-lived user token.")
    page = request(version, page_id, {"fields": "id,access_token"}, long_lived)
    if str(page.get("id")) != page_id or not page.get("access_token"):
        raise SetupError("Page token unavailable. Confirm the user has access to the requested Page.")
    return page["access_token"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--page-id", required=True)
    parser.add_argument("--app-id", default=os.environ.get("META_CLIENT_ID"))
    parser.add_argument("--api-version", default=os.environ.get("META_API_VERSION", "v26.0"))
    parser.add_argument("--env-file", type=Path, default=Path(".env"))
    args = parser.parse_args()
    if not args.page_id.isdigit() or not re.fullmatch(r"v\d+\.\d+", args.api_version):
        parser.error("Use a numeric Page ID and API version such as v26.0")
    app_id = args.app_id or input("Meta app ID: ").strip()
    if not app_id.isdigit():
        parser.error("Meta app ID must be numeric")
    try:
        secret = os.environ.get("META_CLIENT_SECRET") or getpass.getpass("Meta app secret: ")
        token = getpass.getpass("Valid Facebook user access token (with Page permissions): ")
        if not secret or not token:
            raise SetupError("An app secret and valid user authorization are required.")
        page_token = provision(args.api_version, app_id, secret, token, args.page_id)
        save_token(args.env_file, page_token)
        print("Page token saved privately. Recreate social-media-service to load the updated environment.")
        print("Page tokens can still be invalidated by revoked authorization or changes to account access.")
    except (SetupError, OSError) as failure:
        # OS errors may contain sensitive paths; remote errors are sanitized above.
        parser.exit(1, (str(failure) if isinstance(failure, SetupError) else "Unable to write the environment file.") + "\n")


if __name__ == "__main__":
    main()
