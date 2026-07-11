#!/bin/bash
while true; do
  claude "How are you" &
  echo $! > /tmp/claude-automator.pid
  wait $!
done
