#!/usr/bin/env bash
# Signs one support-recovery request, for whoever holds the licence private key.
#
#   ./sign-support-recovery.sh private_key.pem "PC-01 | A1B2C3D4E5F60718 | 2026-09-07 14:05:33"
#
# The argument is exactly what the customer's recovery window shows and its Copy button puts
# on the clipboard: machine | nonce | issued-at. Send the printed line back; the operator
# pastes it into "الرد الموقَّع".
#
# It is one request, for one machine, answerable once, and it stops being accepted
# thirty minutes after the machine issued it - so sign the request you were just sent,
# never one kept from a previous call.
#
# NEVER commit private_key.pem. It is git-ignored, and it is the only thing in this system
# that can produce a valid response; the program itself holds only the public half.
set -euo pipefail

if [ "$#" -ne 2 ]; then
    echo "usage: $0 <private_key.pem> \"<machine> | <nonce> | <issued-at>\"" >&2
    exit 2
fi

key="$1"
request="$2"

[ -f "$key" ] || { echo "no such key file: $key" >&2; exit 2; }

# Rebuild the exact text the program signs. It differs from what is displayed: the tag is
# added and the spaces around the separators are dropped, so both sides agree byte for byte.
machine=$(printf '%s' "$request" | awk -F' *\\| *' '{print $1}')
nonce=$(printf '%s' "$request" | awk -F' *\\| *' '{print $2}')
issued=$(printf '%s' "$request" | awk -F' *\\| *' '{print $3}')

if [ -z "$machine" ] || [ -z "$nonce" ] || [ -z "$issued" ]; then
    echo "the request must read: <machine> | <nonce> | <issued-at>" >&2
    exit 2
fi

payload="HAMZA_RECOVERY|${machine}|${nonce}|${issued}"

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

printf '%s' "$payload" > "$work/payload.txt"
# Written as bytes and base64'd afterwards, never piped as text - the licence signing
# procedure learned that the hard way (README, 2026-03-01).
openssl dgst -sha256 -sign "$key" -out "$work/signature.bin" "$work/payload.txt"

payload_b64=$(openssl base64 -A -in "$work/payload.txt")
signature_b64=$(openssl base64 -A -in "$work/signature.bin")

echo "signed: ${payload}" >&2
echo
echo "${payload_b64}.${signature_b64}"
