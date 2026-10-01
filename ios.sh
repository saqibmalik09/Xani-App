#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/ios-client"
command -v xcodegen >/dev/null && xcodegen
UDID="$(xcrun xctrace list devices 2>&1 | sed -n 's/.*(\([0-9A-Fa-f-]\{25,\}\))$/\1/p' | head -1)"
if [ -z "$UDID" ]; then
  echo "Connect an iPhone, tap Trust, then run ./ios.sh again."
  exit 1
fi
xcodebuild -project App.xcodeproj -scheme App -configuration Debug \
  -destination "id=$UDID" -allowProvisioningUpdates
