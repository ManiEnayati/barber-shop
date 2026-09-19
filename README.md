# Barber Shop

## Barber approval and active sessions

Barber access is activated only when an administrator approves a pending barber
application. Approval updates database roles immediately, but an HTTP session keeps
the authorities issued at OTP login. An approved user should complete OTP
authentication again before relying on BARBER-authorized endpoints. `GET /api/me`
loads the current roles from the database and therefore reflects approval immediately.
