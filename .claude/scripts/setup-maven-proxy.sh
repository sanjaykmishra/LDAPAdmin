#!/bin/bash
# Generates ~/.m2/settings.xml with proxy credentials from JAVA_TOOL_OPTIONS.
# Called by Claude Code SessionStart hook.

if [ -z "$JAVA_TOOL_OPTIONS" ]; then
  exit 0
fi

PROXY_HOST=$(echo "$JAVA_TOOL_OPTIONS" | grep -oP '(?<=-Dhttps\.proxyHost=)\S+')
PROXY_PORT=$(echo "$JAVA_TOOL_OPTIONS" | grep -oP '(?<=-Dhttps\.proxyPort=)\S+')
PROXY_USER=$(echo "$JAVA_TOOL_OPTIONS" | grep -oP '(?<=-Dhttps\.proxyUser=)\S+')
PROXY_PASS=$(echo "$JAVA_TOOL_OPTIONS" | grep -oP '(?<=-Dhttps\.proxyPassword=)\S+')

if [ -z "$PROXY_HOST" ] || [ -z "$PROXY_PORT" ]; then
  exit 0
fi

mkdir -p ~/.m2

cat > ~/.m2/settings.xml << SETTINGS
<settings>
  <proxies>
    <proxy>
      <id>http-proxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>${PROXY_HOST}</host>
      <port>${PROXY_PORT}</port>
      <username>${PROXY_USER}</username>
      <password>${PROXY_PASS}</password>
    </proxy>
    <proxy>
      <id>https-proxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>${PROXY_HOST}</host>
      <port>${PROXY_PORT}</port>
      <username>${PROXY_USER}</username>
      <password>${PROXY_PASS}</password>
    </proxy>
  </proxies>
</settings>
SETTINGS
