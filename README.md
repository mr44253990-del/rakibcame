# Arena Browser Agent (Android)

This project was rebuilt from the original repo configuration into a **safe browser-assistant Android app**.

## What it does

- Chat-first UI with separate chat sessions
- Real Android `WebView` browser preview
- Multi-tab browsing, including background tabs
- Sequential multi-step task execution with status updates
- Generic browser actions: open URL, new tab, switch tab, close tab, back, forward, refresh, search, scroll
- DOM actions: click CSS selector, type into selector, extract text from selector, extract full page text
- Automatic retry for selector actions
- Live DOM preview updates
- Thinking / execution trace panel
- Session-based action history
- Session-based memory notes
- Clipboard aliases such as `{{headline}}`
- Optional Mistral planning from natural-language prompts
- In-app AI settings screen for API key, model, base URL, test, save
- Runtime tools panel for quick tabs, refresh, docs, new chat, and cleanup

## Safety limits

This app intentionally **does not automate**:

- temporary email workflows
- account creation abuse
- OTP / verification harvesting
- bypassing site restrictions

## Setup

1. Open in Android Studio.
2. Create a `.env` file from `.env.example`.
3. Add your own `MISTRAL_API_KEY`.
4. Or open the in-app **Settings** tab and save API key, model, and base URL there.
5. Build and run.

## Example prompts

- `open https://example.com`
- `click button.primary`
- `type hello world into input[name='q']`
- `extract h1 as headline`
- `new tab https://developer.android.com`
- `remember use the second tab for docs`

## Notes

- The previous camera-specific code was removed.
- Do **not** commit real API keys or GitHub tokens.
