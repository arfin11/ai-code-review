# AI-Powered GitHub PR Review System

Automated pull request review for Java repositories using a GitHub App, Spring Boot, Kafka, Redis, PostgreSQL, and LLM-based analysis through LangChain4j.

## Overview

This service listens for GitHub pull request webhooks, validates and deduplicates them, pushes accepted review jobs to Kafka, reviews changed Java files with AI agents, and publishes the result back to GitHub as commit statuses and check-run annotations.

The current implementation is optimized around:

- GitHub App installation authentication
- Asynchronous review execution through Kafka
- Java-file-only review scope
- Review status persistence in PostgreSQL
- Rate limiting in Redis
- Observability through Prometheus metrics

## End-to-end flow

1. A GitHub App webhook hits `POST /webhook`.
2. The controller validates the signature, filters unsupported events/actions, checks repository rate limits, and blocks duplicate delivery IDs.
3. Accepted requests are stored as `RECEIVED` and published to Kafka.
4. The Kafka consumer marks the review `IN_PROGRESS` and starts PR review execution.
5. The review service fetches PR files from GitHub and keeps only reviewable Java files.
6. The orchestrator builds diff-based review context and opens a bounded full-file fetch session for the AI tools.
7. General and security review agents run in parallel.
8. Findings are aggregated, deduplicated, severity-normalized, and filtered for validity.
9. The service posts commit status and a GitHub check run with inline annotations.
10. Review status is marked `SUCCESS` or `FAILED`.

## Detailed block diagram

```text
                                          +------------------------------+
                                          |          GitHub App          |
                                          |  PR labeled / synchronize    |
                                          +--------------+---------------+
                                                         |
                                                         | webhook
                                                         v
+--------------------------------------------------------------------------------------------------+
|                                        Spring Boot Application                                   |
|                                                                                                  |
|  +-------------------------------+                                                                |
|  | TraceIdFilter                 |                                                                |
|  | - creates/propagates traceId  |                                                                |
|  +---------------+---------------+                                                                |
|                  |                                                                                |
|                  v                                                                                |
|  +-------------------------------+      +---------------------+      +-------------------------+  |
|  | GitHubWebhookController       |----->| SignatureValidator  |      | ObjectMapper           |  |
|  | - accepts /webhook            |      | - HMAC verification |      | - payload parsing      |  |
|  | - event/action filtering      |      +---------------------+      +-------------------------+  |
|  | - label check: ai-review      |                                                                |
|  +---------------+---------------+                                                                |
|                  |                                                                                |
|      +-----------+-----------+                                                                    |
|      |                       |                                                                    |
|      v                       v                                                                    |
|  +-----------+         +----------------------+                                                   |
|  | Redis     |         | IdempotencyService   |                                                   |
|  | rate limit|         | + ProcessedEvent JPA |                                                   |
|  +-----+-----+         +----------+-----------+                                                   |
|        |                           |                                                               |
|        +------------- allow -------+                                                               |
|                                    |                                                               |
|                                    v                                                               |
|                        +---------------------------+                                               |
|                        | PRReviewStatusService     |                                               |
|                        | - mark RECEIVED          |                                               |
|                        | - persist status rows    |                                               |
|                        +------------+--------------+                                               |
|                                     |                                                              |
|                                     v                                                              |
|                        +---------------------------+                                               |
|                        | PRReviewProducer          |                                               |
|                        | - publish PRReviewEvent   |                                               |
|                        +------------+--------------+                                               |
+-------------------------------------|------------------------------------------------------------+
                                      |
                                      | Kafka topic: ai-review-events-v2
                                      v
                           +---------------------------+
                           | PRReviewConsumer          |
                           | - mark IN_PROGRESS        |
                           | - retry on failure        |
                           | - DLT handler             |
                           +------------+--------------+
                                        |
                                        v
                           +---------------------------+
                           | PRReviewService           |
                           | - fetch PR files          |
                           | - keep .java only         |
                           | - cap files per PR        |
                           +------------+--------------+
                                        |
                                        v
                           +---------------------------+
                           | GitHubService             |
                           | - list PR files           |
                           | - fetch latest SHA        |
                           | - create check run        |
                           | - set commit status       |
                           +------------+--------------+
                                        |
                                        v
                           +---------------------------+
                           | PRReviewOrchestrator      |
                           | - build contexts          |
                           | - run review agents       |
                           | - aggregate/verify        |
                           +------------+--------------+
                                        |
                 +----------------------+----------------------+
                 |                                             |
                 v                                             v
     +---------------------------+                 +---------------------------+
     | FileContextService        |                 | FileContextTool           |
     | - diff snippet building   |                 | - fetch full file from    |
     | - changed line extraction |                 |   GitHub on demand        |
     | - context truncation      |                 | - bounded by policy       |
     +---------------------------+                 +-------------+-------------+
                                                                 |
                                                                 v
                                                   +---------------------------+
                                                   | GitHub contents API       |
                                                   +---------------------------+

                                        +--------------------------------------+
                                        | Parallel AI review agents            |
                                        | - GeneralReviewAI                    |
                                        | - SecurityReviewAI                   |
                                        +----------------+---------------------+
                                                         |
                                                         v
                                        +--------------------------------------+
                                        | ReviewAggregator                     |
                                        | - deduplicate findings               |
                                        | - prefer stronger/security findings  |
                                        +----------------+---------------------+
                                                         |
                                                         v
                                        +--------------------------------------+
                                        | ReviewVerifier                       |
                                        | - normalize severity                 |
                                        | - drop weak/invalid findings         |
                                        +----------------+---------------------+
                                                         |
                                                         v
                                        +--------------------------------------+
                                        | GitHub Checks + Commit Status        |
                                        | - annotations on changed files       |
                                        | - success/failure summary            |
                                        +----------------+---------------------+
                                                         |
                                                         v
                                        +--------------------------------------+
                                        | PRReviewStatusService                |
                                        | - mark SUCCESS / FAILED              |
                                        +--------------------------------------+
```

## Core components

| Component | Responsibility |
| --- | --- |
| `GitHubWebhookController` | Entry point for webhook validation, filtering, rate limiting, idempotency, and event publishing |
| `PRReviewProducer` | Publishes accepted review events to Kafka |
| `PRReviewConsumer` | Consumes review jobs, manages retries, and transitions execution state |
| `PRReviewService` | Fetches PR data, filters supported files, and publishes final GitHub results |
| `PRReviewOrchestrator` | Builds context, runs AI reviewers, aggregates findings, and verifies output |
| `FileContextService` | Converts GitHub patch diffs into bounded AI review context |
| `FileContextTool` | Lets review agents fetch full file contents from GitHub within policy limits |
| `GitHubService` | Handles GitHub REST API calls for PR files, statuses, comments, and check runs |
| `PRReviewStatusService` | Persists lifecycle state: `RECEIVED`, `IN_PROGRESS`, `SUCCESS`, `FAILED` |
| `IdempotencyService` | Prevents duplicate delivery processing using persisted webhook delivery IDs |
| `RateLimiterService` | Protects the service with per-repository Redis-based throttling |

## Data stores and infrastructure

### PostgreSQL

PostgreSQL stores durable application state through Spring Data JPA:

- `processed_events`: processed GitHub delivery IDs for idempotency
- `pr_review_status`: review lifecycle state, repository, PR number, installation ID, and failure reason

### Redis

Redis is used for repository-scoped rate limiting. The service uses a Lua script loaded from `src/main/resources/scripts/rate-limiter.lua`.

### Kafka

Kafka decouples webhook ingestion from review execution. The configured topic is:

```properties
topic.pr-review=ai-review-events-v2
```

### Observability

The application exposes:

- Spring Boot Actuator endpoints
- Prometheus metrics

## Review pipeline details

### 1. Webhook acceptance rules

The current webhook controller only accepts:

- GitHub event: `pull_request`
- Actions: `labeled`, `synchronize`
- For `labeled`, the label must be `ai-review`

Requests are rejected or ignored when:

- the webhook signature is invalid
- the event type is not `pull_request`
- the action is unsupported
- the installation ID is missing
- the repository exceeds the rate limit
- the delivery ID was already processed

### 2. File selection policy

The current implementation reviews only Java files:

```text
Allowed extension: .java
Maximum files per PR: 40
```

### 3. Context-building policy

The review context is built from patch hunks, with emphasis on added lines. Current limits:

```text
Patch context max chars: 4000
Full-file fetch max chars: 12000
Full-file fetch calls per PR: 5
Full-file fetch calls per file: 1
```

### 4. Dual-agent review

Two review agents run in parallel:

- general review
- security review

The output is then:

1. aggregated and deduplicated
2. sorted by severity
3. verified to remove invalid or weak findings

### 5. GitHub output

Results are posted back to GitHub as:

- commit status with context `AI Code Review`
- check run with summary and inline annotations

The check run currently publishes up to 50 annotations in one request.

## Status lifecycle

Review execution status is persisted with the following lifecycle:

```text
RECEIVED -> IN_PROGRESS -> SUCCESS
                     \-> FAILED
```

## Local setup

## Prerequisites

- Java 21
- Maven
- PostgreSQL
- Redis
- Kafka
- A GitHub App with pull request webhook delivery enabled
- An OpenAI API key

## Environment variables

Set the following values before starting the application:

```bash
OPENAI_API_KEY=your_openai_key
GITHUB_APP_ID=your_github_app_id
GITHUB_PRIVATE_KEY_PATH=path_to_github_app_private_key
GITHUB_WEBHOOK_SECRET=your_webhook_secret
PORT=8080
```

## Application configuration

The default `application.properties` expects:

- PostgreSQL on `localhost:5432`
- Redis on `localhost:6379`

Review the following properties before local startup:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/ai_code_reviewer?options=-c%20TimeZone=Asia/Kolkata
spring.datasource.username=postgres
spring.data.redis.host=localhost
spring.data.redis.port=6379
server.port=${PORT:8080}
topic.pr-review=ai-review-events-v2
```

## Run locally

1. Start PostgreSQL, Redis, and Kafka.
2. Export the required environment variables.
3. Start the application:

```bash
mvn spring-boot:run
```

4. Expose the service publicly if GitHub must reach your local machine:

```bash
ngrok http 8080
```

5. Configure the GitHub App webhook URL to point to:

```text
https://<your-public-url>/webhook
```

## Usage

Trigger a review by either:

- adding the `ai-review` label to a pull request
- pushing new commits to an already tracked pull request when GitHub emits `synchronize`

On success, GitHub will show:

- a commit status named `AI Code Review`
- a check run containing a summary and inline annotations

## Tech stack

- Java 21
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Kafka
- Spring Data Redis
- PostgreSQL
- Redis
- Kafka
- LangChain4j
- OpenAI chat model
- Micrometer and Prometheus

## Current limitations

- review scope is limited to `.java` files
- only `pull_request` webhook events are processed
- `labeled` and `synchronize` are the only supported actions
- full-file context fetches are tightly budgeted
- check-run annotations are capped at 50 per request

## Screenshots

### Inline review comment in pull request changes view

<img width="948" height="464" alt="image" src="https://github.com/user-attachments/assets/5d57f297-da7d-4b43-a861-5584fbeb1665" />


### AI Code Review check run summary and annotations

<img width="941" height="470" alt="image" src="https://github.com/user-attachments/assets/3d9916f2-ddc2-494c-9df1-60f6401a12a1" />

