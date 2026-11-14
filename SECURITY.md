# Security policy

## Supported versions

Security fixes are made against the latest source on the default branch. If you use a released APK, update to the latest available release before reporting an issue when possible.

## Report a vulnerability privately

Please do not disclose suspected vulnerabilities in a public issue, discussion, or pull request. Use GitHub's **Report a vulnerability** action in the repository Security tab when it is available. If private vulnerability reporting is unavailable, contact the repository owner privately through GitHub and include a way to follow up.

Please include:

- The affected app version, Android version, and device or emulator details.
- A concise description of the impact and steps to reproduce it.
- Relevant logs or a proof of concept, with secrets and personal data removed.
- Whether the issue is known to affect a released build or only a development build.

Do not include passwords, session cookies, account tokens, private backups, or other people's data in a report. Avoid testing against accounts, services, or devices you do not own or have permission to test.

The maintainer will make a best effort to acknowledge and investigate private reports. Please allow time for a fix before sharing vulnerability details publicly; no fixed response or disclosure deadline is promised.

## Secret handling

Never commit signing keys, credentials, API tokens, account cookies, backup files, or private user data. If a secret was committed, revoke or rotate it first; removing it from a later commit does not make the exposed secret safe.
