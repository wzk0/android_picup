#!/usr/bin/env bash
#
# Creates the release signing material PicUp needs, once.
#
# Writes release.keystore (kept out of Git) plus a local release-signing.env a
# shell can source, then prints the four GitHub Actions secrets to paste into
# the repository. Nothing here is committed: *.keystore and
# release-signing.env are already in .gitignore.
#
# Usage:
#   ./scripts/release-signing.sh                 # create, or re-print if it exists
#   ./scripts/release-signing.sh --force         # replace the keystore (a NEW key)
#   ./scripts/release-signing.sh --out path      # keystore somewhere else
#
# Losing the keystore means every future build is a different app to Android:
# the installed one cannot be updated in place, only uninstalled and
# reinstalled. Back release.keystore up with its passwords.

set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
keystore="$repo_root/release.keystore"
env_file="$repo_root/release-signing.env"
alias_name=${KEY_ALIAS:-picup}
dname=${DN:-"CN=github.wzk0.picup, OU=Android, O=wzk0, C=CN"}
force=0

while [ $# -gt 0 ]; do
    case "$1" in
        --force) force=1 ;;
        --out)
            shift
            [ $# -gt 0 ] || { echo "--out needs a path" >&2; exit 2; }
            keystore=$1
            env_file="$(dirname "$keystore")/release-signing.env"
            ;;
        -h|--help)
            sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $1" >&2
            exit 2
            ;;
    esac
    shift
done

command -v keytool >/dev/null 2>&1 || {
    echo "keytool not found. Install a JDK (17 or newer) and try again." >&2
    exit 1
}

# GNU base64 takes -w; BSD/macOS does not.
if base64 -w0 </dev/null >/dev/null 2>&1; then
    b64() { base64 -w0 "$1"; }
else
    b64() { base64 <"$1" | tr -d '\n'; }
fi

existing=0
if [ -e "$keystore" ] && [ "$force" -ne 1 ]; then
    existing=1
    if [ ! -f "$env_file" ]; then
        echo "Keystore exists ($keystore) but $env_file is gone, so its passwords are unknown." >&2
        echo "Restore the passwords as STORE_PASSWORD/KEY_PASSWORD, or pass --force to make a new key." >&2
        exit 1
    fi
    # shellcheck disable=SC1090
    . "$env_file"
    store_password=${PICUP_KEYSTORE_PASSWORD:-${STORE_PASSWORD:-}}
    # PKCS12 keeps one password for the store and for the key inside it.
    key_password=$store_password
    alias_name=${PICUP_KEY_ALIAS:-$alias_name}
    if [ -z "$store_password" ] || [ -z "$key_password" ]; then
        echo "Could not read the passwords from $env_file." >&2
        exit 1
    fi
    keytool -list -keystore "$keystore" -storepass "$store_password" -alias "$alias_name" >/dev/null
else
    # 32 hex characters: strong enough to matter, and legal in a shell, a
    # properties file and a URL-encoded secret alike. od reads a fixed number of
    # bytes, so no stage of this can exit early and trip pipefail.
    generate() { od -An -N16 -tx1 /dev/urandom | tr -d ' \n'; }
    store_password=${STORE_PASSWORD:-$(generate)}
    # A PKCS12 keystore holds a single password for both the store and its key.
    # keytool accepts a different -keypass and ignores it, which only surfaces
    # later as "Given final block not properly padded" when a build tries to
    # sign with the other value — so there is deliberately only one here.
    key_password=$store_password
    if [ -n "${KEY_PASSWORD:-}" ] && [ "$KEY_PASSWORD" != "$store_password" ]; then
        echo "Note: PKCS12 uses one password; KEY_PASSWORD is ignored." >&2
    fi

    mkdir -p "$(dirname "$keystore")"
    rm -f "$keystore"
    echo "Generating $keystore"
    keytool -genkeypair \
        -keystore "$keystore" \
        -storetype PKCS12 \
        -alias "$alias_name" \
        -keyalg RSA \
        -keysize 4096 \
        -validity 10000 \
        -dname "$dname" \
        -storepass "$store_password" \
        -keypass "$key_password" \
        -noprompt >/dev/null
fi

cat >"$env_file" <<EOF
# Local release signing. Source it before ./gradlew :app:assembleRelease.
# Never commit this file; release.keystore must not be committed either.
export PICUP_KEYSTORE_PATH="$keystore"
export PICUP_KEYSTORE_PASSWORD="$store_password"
export PICUP_KEY_ALIAS="$alias_name"
export PICUP_KEY_PASSWORD="$key_password"
EOF
chmod 600 "$env_file" "$keystore"

fingerprint=$(keytool -list -v -keystore "$keystore" -storepass "$store_password" -alias "$alias_name" |
    sed -n 's/.*SHA256: //p' | head -1)

if [ "$existing" -eq 1 ]; then
    echo "Reusing the existing keystore. Pass --force to generate a new key (installed builds would stop updating)."
fi

cat <<EOF

Local signing written to:
  $keystore
  $env_file  (chmod 600, git-ignored)

SHA-256 fingerprint:
  $fingerprint

------------------------------------------------------------------
GitHub repository secrets
  Settings → Secrets and variables → Actions → New repository secret
  https://github.com/wzk0/android_picup/settings/secrets/actions

Name                           Value
PICUP_KEYSTORE_BASE64          $(b64 "$keystore")
PICUP_KEYSTORE_PASSWORD        $store_password
PICUP_KEY_ALIAS                $alias_name
PICUP_KEY_PASSWORD             $key_password   (same as the store password: PKCS12 has one)
------------------------------------------------------------------

Or with the GitHub CLI, from this directory:

  gh secret set PICUP_KEYSTORE_BASE64   < <(base64 -w0 "$keystore")
  gh secret set PICUP_KEYSTORE_PASSWORD --body "$store_password"
  gh secret set PICUP_KEY_ALIAS         --body "$alias_name"
  gh secret set PICUP_KEY_PASSWORD      --body "$key_password"

Signed build on this machine:

  . ./release-signing.env && ./gradlew :app:assembleRelease

Keep $keystore secret: together with these passwords it is the app's identity,
and anyone holding both can publish updates Android will accept.
EOF