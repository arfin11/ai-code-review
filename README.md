# 🚀 AI-Powered GitHub PR Review System

An intelligent, automated code review system that integrates with GitHub Pull Requests and provides actionable feedback using LLMs.

---

## ✨ Overview

This system automatically reviews Pull Requests and adds **inline comments + status checks** using AI.

It simulates real-world tools like **CodeRabbit / GitHub Copilot Reviews**, built using:

* Java + Spring Boot
* GitHub App API
* LLM (via LangChain4j)

---

## 🔌 GitHub App Integration (How Users Use This)

👉 Install the GitHub App:

https://github.com/apps/arfin-ai-code-reviewer

### 📌 Steps:

1. Open the link above
2. Click **Install App**
3. Select your repository
4. Done ✅

---

### 🔁 How it works

```text
User installs GitHub App
        ↓
GitHub sends webhook to your service
        ↓
Your service reviews PR
        ↓
Comments + Check Run appear in PR
```

---

## 🧠 Features

### 🔹 AI Code Review

* Detects:

  * Bugs
  * Security issues
  * Performance issues
* Provides structured feedback

---

### 🔹 Inline PR Comments

* Adds comments directly on changed lines
* Uses GitHub Checks API

---

### 🔹 Smart Line Mapping (No LLM dependency)

* Anchor-based deterministic mapping
* Prevents incorrect line numbers

---

### 🔹 Idempotency

* Prevents duplicate webhook processing

---

### 🔹 Chunking + Batching

* Handles large PRs efficiently

---

### 🔹 Kafka (Optional)

* Enables async processing
* Improves scalability

---

### 🔹 Secure Configuration

* No secrets in code
* Uses ENV variables

---

### 🔹 Logging

* Console (cloud)
* File (local debugging)

---

## 🏗️ Architecture

```text
                 ┌──────────────────────────┐
                 │      GitHub Repo         │
                 │ (PR / Label Events)     │
                 └────────────┬────────────┘
                              │ Webhook
                              ▼
                 ┌──────────────────────────┐
                 │  Webhook Controller      │
                 │ (Validation + Filtering) │
                 └────────────┬────────────┘
                              │
               ┌──────────────┴──────────────┐
               │                             │
               ▼                             ▼
   ┌────────────────────┐        ┌────────────────────┐
   │ Direct Processing  │        │ Kafka Producer     │
   │ (Sync Mode)        │        │ (Async Mode)       │
   └──────────┬─────────┘        └──────────┬─────────┘
              │                              │
              ▼                              ▼
      ┌────────────────────────────────────────────┐
      │           PRReviewService                  │
      │  - Extract diff                           │
      │  - Chunk code                             │
      │  - Map line numbers                       │
      └────────────┬──────────────────────────────┘
                   │
                   ▼
      ┌────────────────────────────────────────────┐
      │              LLM Service                   │
      │  - Analyze code                           │
      │  - Generate structured feedback           │
      └────────────┬──────────────────────────────┘
                   │
                   ▼
      ┌────────────────────────────────────────────┐
      │          GitHub Checks API                 │
      │  - Inline comments                        │
      │  - Pass/Fail status                       │
      └────────────────────────────────────────────┘
```

---

## 🧩 Component Roles

| Component        | Responsibility             |
| ---------------- | -------------------------- |
| GitHub App       | Sends webhook events       |
| Controller       | Validates & filters events |
| Kafka (optional) | Async buffering & scaling  |
| PRReviewService  | Core processing logic      |
| LLM Service      | AI analysis                |
| GitHub API       | Adds comments & checks     |

---

## 🖼️ Demo (How it works)

### 🔹 PR Trigger

<img width="1659" height="725" alt="image" src="https://github.com/user-attachments/assets/36d3b532-b127-42f5-8dac-c774fa973b33" />


---

### 🔹 AI Review Summary

<img width="1446" height="723" alt="image" src="https://github.com/user-attachments/assets/aba212a1-fb70-4f00-98b4-2b94346ede23" />

---

### 🔹 Inline Code Comments
<img width="1335" height="711" alt="image" src="https://github.com/user-attachments/assets/c997938e-94f7-4997-984a-36a55bb0aa1c" />

---

## ⚙️ Tech Stack

* Java 21
* Spring Boot
* LangChain4j
* Kafka (optional)
* H2 DB
* Logback
* GitHub App API

---

## 🚀 Local Setup

### 1. Clone repo

```bash
git clone https://github.com/<your-username>/ai-code-review.git
cd ai-code-review
```

---

### 2. Set ENV variables

```bash
export OPENAI_API_KEY=your_key
export GITHUB_WEBHOOK_SECRET=your_secret
export GITHUB_APP_ID=your_app_id
export GITHUB_PRIVATE_KEY=your_key
```

---

### 3. Run app

```bash
mvn spring-boot:run
```

---

### 4. Expose webhook

```bash
ngrok http 8080
```

---

### 5. Configure webhook

* URL: `https://your-ngrok-url/webhook`
* Events: PR + Label
* Secret: same as ENV

---

### 6. Test

* Create PR
* Add label: `ai-review`
* See comments + checks

---

## ⚡ Kafka Mode (Optional)

Enable:

```properties
kafka.enabled=true
```

Flow:

```text
Webhook → Kafka → Consumer → Review
```

---

## 🔒 Security

* ENV-based secrets
* GitHub signature validation
* No secrets in repo
---


## 🚀 Future Improvements

* AST-based analysis
* Multi-file context
* Smart deduplication
* UI dashboard

---
