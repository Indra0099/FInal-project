# Enable Gemini with a personal free-tier key

ShardQuery now uses Google Gemini instead of the paid OpenAI API. The source package does **not** contain a shared key. Each new installation needs its owner’s personal Google credential. The database explorer continues to work without any AI key.

## 1. Create your own key

1. Open [Google AI Studio API keys](https://aistudio.google.com/apikey).
2. Sign in with your **personal** Google account, not your office account.
3. Choose or create a personal project. Review any terms or verification steps yourself.
4. Confirm the project shows **Free Tier**. Do not enable billing or attach a paid billing account for this demonstration.
5. Create/copy the API key. Do not post it in chat, screenshots or GitHub.

Google documents free-tier access with model-specific quotas and eligibility requirements. A model name does not make a billing-enabled account free. The application cannot determine your account tier, and pricing/availability can change. See [Gemini billing](https://ai.google.dev/gemini-api/docs/billing) and [model pricing](https://ai.google.dev/gemini-api/docs/pricing).

## 2. Save the key locally

Open `.env` in the project root and fill only the key value:

```dotenv
GEMINI_API_KEY=your_personal_free_tier_key
GEMINI_MODEL=gemini-3.1-flash-lite
EMBEDDING_MODEL=gemini-embedding-001
```

The default chat and embedding models are listed with free-tier access in the current provider pricing documentation. No key has been invented or bundled. `.env` is ignored by Git and excluded from the source ZIP. Never put the key in React code or a `VITE_` variable. Any old `OPENAI_API_KEY` entry is unused by the application.

## 3. Restart the application

On a normal Docker Desktop / Engine system, from the project root:

```sh
docker compose up --build -d app
```

On this development computer, use its isolated Colima context:

```sh
docker --context colima-shardquery compose up --build -d app
```

Open **http://localhost:8087** and refresh. “Configured” means only that the key is present; it does not verify account access or billing status.

## 4. Verify actual AI execution

1. Ask “Which laptops cost more than ₹50,000?” in Database mode. Inspect the real records and AI answer.
2. Upload `samples/return-policy.txt`. Wait for **Ready**. For the existing waiting upload, select **Retry indexing** after the restart.
3. Ask “What does the uploaded return policy say about laptop returns?” in Documents mode. Open the cited excerpts.
4. Upload the review samples with their product/customer IDs from `samples/README.md`, wait for Ready, then try the combined example.
5. A completed generated answer is labelled `GEMINI` in the API result. A contract-test response is never used by the normal app.

## Quotas and data handling

A free-tier rate limit produces a visible error after a bounded retry; the application does not switch to a paid provider. Wait for quota recovery instead of enabling billing. Use the supplied fictional data for the demonstration. Google's free-tier terms may permit use of submitted content to improve its products; review the [current terms](https://ai.google.dev/gemini-api/terms) before uploading private information.

## How the integration works

`GeminiGateway.java` calls Google's [OpenAI-compatible REST endpoints](https://ai.google.dev/gemini-api/docs/openai) for structured JSON planning, answers and embeddings. No additional AI framework was added. Native 3072-value `gemini-embedding-001` vectors are reduced to their first 1536 values and normalized using the [documented reduced-dimension method](https://ai.google.dev/gemini-api/docs/embeddings#quality-for-smaller-dimensions). Invalid dimensions, non-numeric/zero vectors and incomplete responses are rejected. Qdrant continues to use 1536 dimensions and filters documents by embedding model. Gemini may omit embedding indexes; entirely positional batches preserve input order. Citation formatting gets at most one correction attempt with the original evidence. Unknown references are rejected even after retry.

Previously indexed OpenAI vectors are not searched with Gemini vectors. Upload the source again under the new model if you have older READY documents; existing WAITING_FOR_KEY documents can be retried. There is no destructive deletion of prior files or history.

## Verification boundary

The final suite passed 32 Java tests with real infrastructure and a test-only HTTP provider. On the development computer, the personal project was observed as Free tier and actual Gemini SQL, document indexing, RAG and hybrid queries completed. See [verification results](VERIFICATION.md). This does not establish another account’s tier or guarantee model accuracy; repeat the live checks above on a new installation.
