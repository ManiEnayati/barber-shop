# Barber Shop

## Barber approval and active sessions

Barber access is activated only when an administrator approves a pending barber
application. Approval updates database roles immediately, but an HTTP session keeps
the authorities issued at OTP login. An approved user should complete OTP
authentication again before relying on BARBER-authorized endpoints. `GET /api/me`
loads the current roles from the database and therefore reflects approval immediately.

## Booking provenance and future commission policy

`BookingSource` is durable provenance and must not change when an appointment is
claimed or moves through its lifecycle. Customer self-bookings use `CUSTOMER`; the
future commission policy is 10% of realized Barber revenue. Barber-created manual
Guest bookings use `BARBER`; their future platform commission is zero. Claiming a
`BARBER` booking neither changes its source nor establishes prior customer acceptance.

No financial calculation is implemented yet. In particular, `NO_SHOW` alone does not
prove zero realized revenue, and an unresolved or disputed no-show report is not a
proven Customer absence. Future finalized rules may treat a forfeited deposit on a
`CUSTOMER` booking as realized Barber revenue.
