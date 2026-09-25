# The licence contract with the licence server

Copied byte for byte from the server's repository (`accountk-license-server`, `src/test/resources/contract/`,
commit `ff07e86`), and read by `LicenseContractTest` through the real `LicenseEvaluator`. The server's own
`LicenseContractTest` reads the same files. A change of format in either repository breaks the other's test
the day the new files are copied across - which is the point (`server-plan.md` §7.3 there,
`docs/licensing-server-plan.md` §9 here).

| File | What it is |
|---|---|
| `TEST-ONLY-license-server-public.pem` | The public half of a key made **once** to sign the two files below, and destroyed straight after. It must never enter `LicenseServerKey`, and `LicenseContractTest` fails if it does. |
| `accountk-license-perpetual.dat` | `HAMZA_LICENSE2\|3f2a9c4e-8b1d-4e6f-a07c-5d9e2b1f4c83\|C00042\|full\|2026-09-24\|2027-09-24\|-` |
| `accountk-license-subscription.dat` | `HAMZA_LICENSE2\|3f2a9c4e-8b1d-4e6f-a07c-5d9e2b1f4c83\|C00042\|pos\|2026-09-24\|2027-09-24\|2027-10-24` |

**No private key is here or anywhere else**: nobody can sign anything with the key these files were signed
by, real or test. Do not edit these files; a new format is a new tag and new files from the server.
