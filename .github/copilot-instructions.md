# GitHub Copilot Instructions

When performing a pull request code review, respond in Korean.

Review backend changes with these project rules:

- Keep comments concise and actionable.
- Prioritize bugs, security issues, missing validation, transaction boundary risks, and missing tests.
- Check that API responses follow the `ApiResponse` format.
- Check that `.env` values, DB passwords, tokens, keys, and other secrets are not committed.
- Check that request DTOs use appropriate Bean Validation annotations.
- Check that JPA entity mappings match the ERD rules.
- Preserve the unique `(meeting_id, member_id)` rule for meeting participation.
- Verify that meeting joins are blocked when a meeting is `COMPLETED`, `CANCELED`, or full.
- Verify that only the host can update, cancel, complete, or delete their meeting.
- Prefer focused comments over broad style advice.

