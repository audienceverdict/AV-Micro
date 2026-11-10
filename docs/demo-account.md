# Demo account login

Demo login is disabled by default. Enable it only on a testing deployment with an
active `dev`, `local`, `test`, or `otp-test` Spring profile. Startup fails if `prod`
or `production` is also active, or if the configured email/code is invalid.

Set these environment variables for identity-service and restart:

```sh
AUTH_DEMO_ENABLED=true
AUTH_DEMO_EMAIL=demo@example.test
AUTH_DEMO_OTP=123456
```

Equivalent Spring properties are `auth.demo.enabled`, `auth.demo.email`, and
`auth.demo.otp`. The OTP must be six digits, matching existing API validation.
Ensure remote configuration does not override these properties.

On startup, the service creates an ordinary enabled user named Demo User with the
configured email and no mobile number. An existing ordinary enabled user is
reused without modification. A disabled, deleted, or privileged account causes
startup to fail; choose a dedicated demo email. Use synthetic test data.

Request a fresh login challenge using the existing endpoints:

```http
POST /api/v1/auth/otp/request
Content-Type: application/json

{"destination":"demo@example.test"}
```

```http
POST /api/v1/auth/otp/verify
Content-Type: application/json

{"destination":"demo@example.test","otp":"123456"}
```

The demo login skips email delivery and stores a BCrypt hash of the configured
code. Verification returns the usual access token. Each challenge expires and
can be used only once; resend cooldown, hourly request limits and attempt limits
still apply. Request a new challenge before every subsequent login.

Other emails and registration, WhatsApp, and deletion challenges retain normal
random codes and delivery. Setting `AUTH_DEMO_ENABLED=false` and restarting
restores normal email OTP login for the account; it does not delete the user.
Already issued challenges and access tokens retain their normal lifetimes.
