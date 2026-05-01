# 🚀 AI Code Reviewer (GitHub App)

An AI-powered system that automatically reviews Pull Requests and adds inline comments + status checks.

---

## 🔌 Install GitHub App

👉 https://github.com/apps/arfin-ai-code-reviewer

### Steps:

1. Click **Install App**
2. Select your repo
3. Add label `ai-review` on any PR

---

## ⚙️ How it Works

```text
GitHub PR → Webhook → Service → LLM → GitHub Checks API
```

---

## 🏗️ Architecture

```text
GitHub App
   ↓ (Webhook)
Controller (Validation)
   ↓
[Sync Mode] → PRReviewService
[Async Mode] → Kafka → Consumer → PRReviewService
   ↓
LLM Service (AI analysis)
   ↓
GitHub Checks API (comments + status)
```

---

## 🧩 Components

* **Controller** → Handles webhook events
* **Kafka (optional)** → Async + scalable processing
* **PRReviewService** → Core logic (diff, chunking, mapping)
* **LLM Service** → AI-based code review
* **GitHub API** → Adds comments + blocks PR

---

## ✨ Features

* 🤖 AI-based code review
* 📍 Accurate inline comments (no LLM line dependency)
* ⚡ Handles large PRs (chunking)
* 🔁 Idempotent webhook handling
* 🔒 Secure (ENV-based config)
* 🧠 Hybrid approach (rules + AI)

---

## 🖼️ Demo

### PR Trigger & Check

![Image](https://images.openai.com/static-rsc-4/4gqyPhCZ6Dm_3pN0l-xsRzF-ewhdVmW3Dc_xc_CbyI4ameJKnHWmtPt326BhAMgc1tMwBZF8Bt1Qw61e0iLRIguIqs2QONw6bLZrsrdxIJEf3u1bxaYLitBwc2j42IhUdqua6LZvWxD_HCrODMamU2pWPq0Aw2AvnzfOW-2b7E-qxreJ2MdCFE2H3h1jOtro?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/OXfiFo74ENJuy6l9NJ-UnI1TsMDA0A1VbuJ1q8vgVS0or5ldcy7t2JuNKykeNz3ZyZKkFGjjgxLX2rCNAQ3B9XUgBXpjDkkq_BC_9WfPkauIzmwOLYXbkI_Y70L5zEt02GIbChqvAUAOT---vHIHC4ZoXeLaCsMAQXiZNkC8lk9jfA7O4HN_XVMayyF-oWLH?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/sMrRDDV9VvBxkBQwRsRZtZFRrZL2Mgtjw8Uv1fwerZQjWVhZiQzBw8a-uHEXXQSa6KoO_ve6ftTCPsCyRzl0sHdeToOA5K6Eyohmt5lyIotopzOzNkAw9oJcYYgzuyAn-wJDfSP9jXXCeW5qdxTIFIvE1Yjte_Aq-FfXHWLA8tRZ-VorzjblBq5V_KlbXbG8?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/sfpyxyut8eZauzs8ajk8Zb7yTL3FkVJr89OMyMJvqJcJFlDJXyUG8fT2At2kR6lpH-hI8E1nLEW1shhPwoA3S_sgj8v6EaD7CZLO-kGyJjHTqso4VtfPYiAUdGI52P7eZnIwLT9hPERmAx_1Oa6I3UZ6RhOgr919XW1I6z_O6rxKHm0xAmtTjNkL70KNd0E7?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/pxQ-_zmsvBdPs9R3jiYuxE0NGPgwy5cWUNhIUcCy0WQyUdAPnEgkkgzSbH1h4v79C8125WvNbLYO81FyNEtRuBodne46vB20z94Yy-s4BCbsO6OcakdNCzgGVU8oAsy8B53xp1y5hPofIeEdibL6c5ViVh56lib4TNPoNKxaXOzGI7Zc67aIAz0siKn6eucB?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/JbXg8pCMtybA3-mhNwlisXrxvDN9xumcOyuKoaTbZvzvK78sbJkJTjeW2Vxhml6zLlDHpW4TlhsNA1jWvd34cOCheaZEj8h0TBdwfHOwi8vn6au2Xk9wrXKbZV5NvzVyOG2ozk47hAM5M0iVj-JshiGtJ3Uwj8XxrpzDtOkOevuBzLO894d36tzGTVEGk8zw?purpose=fullsize)

---

### AI Review Summary

![Image](https://images.openai.com/static-rsc-4/4jOcH1d9Sjozn5-h4jyjQkUxyOuwynTagIfCUpzgC0SN-rhln9yMBC8OuZ0EWLlOeH1987tSuIVZRpmxnQ02l6Hf7df3NQGFXH16zbB17CcyibedbWypo2k9vEozaNEr7h7UN6N3Qp2fJL-qJXLqPhhh-UF2wPhvGYVFU1BQZoJ1SLo_4rKs3DOGqCWYrcxm?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/3elnvoXsqlsY82riVVH1L50hrC-6Al36gbeuQcbDuPd3_lSNXA8ECztYpt2-3Tj8BNFS7QlZcALhVpjB508acavWhI0MJTSgCqzCDE05wySs1-yInGefXYI1ZHdm7qOfG2CdqmWnv7-meZI25H-PV9b6BqwDh_wPmJQHeT1RKpCxgpLw9H8_Xx8ZK40AT3Fi?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/NUIeIWSvvnP2Z8kV4_le-Zjtyqd0i1d3P5B-w0nAtvMjtevQuezBgfHNhBe2vbRxkEk8mQvR-ucjMuVbrt0HBcOsn50ZjkFzPdVe8xeBaqqlWpDUNY3OcAQ251eTh07kFMQYefRDE5RbNHIlPsY77jM-qiYdfxyQ0J_gRaoWMGSLu1K2p_NQbCoy1I1dlOjo?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/qMt0_9N8o_JDeEDaEajYIHpzPX3LJ5G-eijySX5D0YBmpbgsQ31VC_DOG2tlPc8T-jXDNFPkQXfoPLq_8kyRpziRvYo6cRxySLY1b3ZGQ1XM7zFYaJ12Gx3y8kpCcSorNB9aCeHcrKfFEUI0lwbxgswW-YEsWkNLvOAn_mukWiP5tFdSXbjzLx1WMVovJ9IA?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/q1h1lE7vewJ9enJBDNSqJzOwEmb2achX02pC7luLxeQfqMlDJM92vKsrssGPwdun5DtxnKTDnMD4itK0yKViQV0bxjyWd4zy2mPyMPqGVsG3rBRq6EvX4rU_VlupNUGmVRyEN0je5L04fQri-TemkbSZUDJFBZHgvOywRX6b45Yc-A4tTepTpA3XUOzfl99T?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/9oi38dcnE4lRJ8nlKsQDRm1TZ6kySdhHoJhKU0aVwiXV9lb8t5lBTga2ZbwE2YGX7s1acuVE58cjUiHD3vkjUV1Wi0iKkDUgmU5YviTM7p_NfZ0U9URVbSZqs2oWcoDN5T7Ncjn6TfIU1dD6O_ijCmUsK1Cz16vl0u0yGstenU4lWY1ypSBPFsD_xX6nTq2X?purpose=fullsize)

---

### Inline Comments

![Image](https://images.openai.com/static-rsc-4/vKWyfBru2w9AwVKcmBBWAbBAXv646poUkpgANdS9QyjggxTXHUohXUduQ_Oyp5vUkKL1EoQOBIEo8zW1ylFAemIrbj1JVRhSOG4TAQa_SIReFFcMBT58H1rbFJ_nsl3fGh588oCCW-cTvYPfl2ZkHi2flaGPosQoVH_rtLN-cjeGvNPSzMLu34MQqfoTbyOb?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/3Hkg6YzuoddzM9-pdkk6u_2SxRkf_AWxKCERxzKqxR_71bvHxmzBefaAVeNsbQy675bXnSbyFugMW2Nr-8Crm1YrZtHmLApdhQZwrACW_kQoD6_W0xc7EWLRbwiiYDzyGwrZSKG0DCRBgzDHiEqRN9Oz95YqjwWnYa2AKh89nJRdIia3ggsTo19OyE6dToKi?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/yYj14MvVBFSfOIJO_QTglWyInxaK_QiSiL7wxMwUzzl6o59bMfway1DMy8wTQKbNZxtS7u1lwIyAaV72Rzyhr18w06cW4Dfi4KbjM1Z2GzgnM-2DGzvArH3KUK0l-_vIzkDDa3OcRNfPO4hsjhjBmEdl4pnD0PRTIav9XaENtyy2L9zDzE154NKaop3Vhz3T?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/63bNP_8pfKPvo5T34Ju1dgBXm4vD7COHq6_kEl3qq2y_JflSj7Cm-FGhJESJpXQRYsl2IwVb6acqy1AEeTyHm2f131ggklLsvKvNMiF8KPaXWiGVIta0NBhZOW5VWWOq8a_YdaWacCeI9Kek63xScDL2tjjSBP2-Tqvi_zDCX2_AZQvT3xWVlRQYyCwnpAx4?purpose=fullsize)

![Image](https://images.openai.com/static-rsc-4/3Hkg6YzuoddzM9-pdkk6u_2SxRkf_AWxKCERxzKqxR_71bvHxmzBefaAVeNsbQy675bXnSbyFugMW2Nr-8Crm1YrZtHmLApdhQZwrACW_kQoD6_W0xc7EWLRbwiiYDzyGwrZSKG0DCRBgzDHiEqRN9Oz95YqjwWnYa2AKh89nJRdIia3ggsTo19OyE6dToKi?purpose=fullsize)

---

## 🚀 Run Locally

```bash
export OPENAI_API_KEY=...
export GITHUB_WEBHOOK_SECRET=...
export GITHUB_APP_ID=...
export GITHUB_PRIVATE_KEY=...

mvn spring-boot:run
```

Expose webhook:

```bash
ngrok http 8080
```


> Designed with both synchronous and Kafka-based asynchronous processing for scalability, while keeping deployment simple.
