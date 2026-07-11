# syntax=docker/dockerfile:1
#
# `builder` clones the mono-repo from GitHub and runs a full `npm ci` (not --omit=dev: ts-node et
# al. are devDependencies but run.ts needs them at runtime, not just build time). The final image
# copies out only claude-automator/ — no .git, no other services, no build tooling.
#
# See docs/deploy/README.md for how to build/run this image.

FROM node:22-slim AS builder

ARG GIT_REF=main

RUN apt-get update && apt-get install -y --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN git clone --depth 1 --branch "${GIT_REF}" \
    https://github.com/jameshskoh/automation-cluster.git /src

WORKDIR /src/claude-automator
RUN npm ci

FROM node:22-slim

RUN apt-get update && apt-get install -y --no-install-recommends tmux \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g @anthropic-ai/claude-code

WORKDIR /app
COPY --from=builder /src/claude-automator /app

# .env.example's paths aren't loaded automatically (dotenv only reads a real .env, which doesn't
# exist in the image), so restate them here as real defaults. Still overridable via `-e`.
ENV UUID_PATH=./data/uuid.txt \
    ACK_ID_PATH=./data/ack-id.txt \
    PID_FILE=./data/claude-automator.pid

# Nothing auto-starts: the outer loop and its ANTHROPIC_API_KEY are wired up manually, in an
# attached tmux session, per docs/deploy/README.md.
CMD ["tail", "-f", "/dev/null"]
