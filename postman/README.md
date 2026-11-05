# AV Postman collection

Import **AV-Microservices.postman_collection.json** and **AV-Local.postman_environment.json** into Postman, then select the imported environment. The collection contains 477 requests covering all 100 Spring controller operations: 97 gateway-facing and 3 private operations. Each request has a sample body where applicable and a response-status assertion. Negative folders cover authentication, roles, DTO required fields, malformed JSON, missing resources, business rules and ticket states.

This is endpoint and scenario coverage, not a claim that every possible input/state combination has been tested. The artifacts were generated and statically checked against the source. Live API/provider execution has not been performed.

## Configure and run

1. Start the stack using the root README. Set `baseUrl` (default `http://localhost:8080`).
2. Replace `email` and `mobile` with a new test email and WhatsApp number you control. The supplied example values are sample data, not usable OTP inboxes. Configure SMTP and WhatsApp provider delivery on the server.
3. Open **01 Registration and login** and send requests individually in the displayed order. After the WhatsApp request, enter the received code in `registrationMobileOtp`; after the registration email request, enter `registrationEmailOtp`. The scripts capture `registrationToken`, `emailVerificationToken`, `userToken` and `userId`. To log in later, request an email OTP, enter `loginOtp`, then verify it. Codes are deliberately blank; no universal OTP is assumed.
4. In **02 Staff login**, set `adminEmail` to the configured administrator identity and `verifierEmail` to an existing verifier account. Enter received `adminOtp`/`verifierOtp` before verification. Staff scripts capture separate `adminToken`/`verifierToken` values. Role assignment requires administrator access; changing a user's primary role alone may not make it a permitted administrator login identity.
5. Run **03 Create test fixtures** after obtaining `adminToken`. This creates movie, person, theatre, screen, draft layout, published layout and show; it captures their IDs and an available `seatId`. Choose a future `showDate` (sample `2030-12-20`). The sample show is paid, with two seats. Repeating this folder creates new fixtures.
6. Run **04 Booking and payment** with `userToken`. This holds and books a seat, captures `bookingId`, retrieves and verifies its payment, then downloads the owner's ticket PNG. `MANUAL_TEST` is sample provider text accepted by the current implementation, which directly records verification; it does not demonstrate payment-provider settlement. For QR scanning, decode the PNG and enter the decoded `AVT1:...` string in `qrPayload`.
7. Use **10 ...** folders as the complete endpoint reference. Run relevant reads, updates and dependent-resource creates before cancellations/deletes. **10 WaitingList** requires `fullShowId` to be an open show with **zero** available seats. Claiming requires an unexpired offered entry in `offeredWaitingListId`. Assign verifier show IDs through the administrator additional-roles request before testing verifier operations.
8. Run negative folders individually after satisfying each description's prerequisites. Ordinary role-denial checks require `userToken` to belong to an enabled USER without staff roles. Invalid/expired/disabled token checks use separate variables. Set `targetUserId` to a disposable test account for account administration. Foreign-resource variables must identify resources owned by a second account.

Do not run the entire collection as one linear workflow. It includes alternative states, duplicate endpoint references, destructive cleanup requests, OTP challenges that pause for delivery, and mutually exclusive success/failure cases. The positive reference folders are not dependency ordered. Select a guided folder or individual scenario instead. After OTPs and tokens are available, the fixtures/booking folders can be run in Collection Runner.

## Scenario prerequisites

| Scenario | Setup |
|---|---|
| OTP cooldown | Send one successful request; repeat before `resendAfterSeconds`. |
| OTP expiration / replay | Wait past `expiresInSeconds` / successfully consume the challenge, then verify the same code. |
| OTP attempt limit | Exhaust the configured attempts on a disposable challenge before running the assertion. |
| Disabled user | Use admin status PATCH on a disposable account; supply its previously valid token as `disabledUserToken`. Restore it afterward. |
| Foreign-resource access | Create resources as a second user; put their IDs in `foreignBookingId`, `foreignReviewId`, `foreignWaitingListId`. Test as the first user. |
| Held/booked seat conflict | Set `heldSeatId` to an active hold owned by another user or `bookedSeatId` to a booked seat on the current show. |
| Booking windows | `closedShowId`: non-OPEN; `notOpenShowId`: future `bookingOpens`; `bookingClosedShowId`: elapsed `bookingCloses`. Choose a seat belonging to each show. |
| Waiting list | Fill an open show completely for joining; join once before duplicate checking; cancel a booking and allow the scheduler to make an offer before claiming. |
| Layout changes | Create an editable draft before saving seats or publishing. After publishing, create a new draft for further edits. |
| Scan success | Confirm any required administrator approval and verify payment; use the ticket owner's decoded QR. |
| Scan rejection states | Each request describes its required booking/ticket state; use separate bookings where needed. Business rejection returns HTTP 200 with `valid:false` and an asserted `result`. |
| Verifier scope | Assign an existing show to the verifier; use an existing unassigned show for rejection testing. |
| Staff roles | `managerToken` and `makerToken` must belong to enabled users with only the indicated additional role; these roles currently confer no catalog write access. |

## Scope and current behavior

- All identity and business controllers are represented, including registration, login, profiles, administration, roles/assignments, movies, media, credits, people/social links, theatres/screens/versioned layouts, shows/availability, holds/bookings/cancellation, payment, reviews/moderation, waiting lists, notifications, verification and scan history.
- **80 Legacy disabled APIs** expects HTTP 409 for valid legacy request bodies. Invalid bodies still fail validation with 400.
- **90/91 Private APIs** use `identityBaseUrl`/`avBaseUrl` with `X-Service-Token` from `serviceToken`. Run from the private network or expose local service ports deliberately; Compose does not publish these ports. They cannot be exercised through the gateway.
- Public payment reads are intentionally sent without a bearer token, matching the current security rules. Payment verification requires authentication; the collection does not invent administrator-only restrictions absent from the implementation.
- Cancellation, deletion, status and role mutations affect referenced data. Use a separate test environment. Campaign/reminder requests send real notifications; use test-only recipients.
- No password login, refresh-token or logout endpoint exists in this repository, so none is fabricated.
- Provider outages, rate-limit windows, races between concurrent users, database failures and all combinations of validation limits require additional controlled execution. Collection request count is not proof of runtime branch coverage.

## Included files

- `AV-Microservices.postman_collection.json`: Postman v2.1 collection, sample bodies and JavaScript assertions/capture scripts.
- `AV-Local.postman_environment.json`: editable sample inputs, separate role tokens and captured resource IDs; no real credentials.
- `endpoint-coverage.json`: source controller inventory of the 100 mapped operations.
- `service-error-inventory.json`: source locations/statuses/codes of explicit `ApiException` branches; an audit reference, not a list of successfully executed tests.
- `generate_collection.py`: dependency-free generator. Run `python3 postman/generate_collection.py` from the repository root to regenerate the JSON artifacts.
- `validate_collection.py`: checks controller-operation coverage, JSON bodies, variables, status assertions, and JavaScript syntax using Node.js.

Tokens and proofs are saved to the selected Postman environment. Keep exported environments with real tokens private.

## SocialMedia operations

SocialMedia operator APIs use `/api/v1/social/**` through the gateway and require
an Identity administrator bearer token. See the [service guide](../SocialMedia/README.md)
and OpenAPI JSON at `/social/v3/api-docs` for its endpoints and configuration.
