---
status: accepted
---

# Bounded AI advisor and data transfer

Repland will define a versioned, typed AI contract now but ship the MVP with a local no-op advisor and no network calls. When AI is enabled later, requests will be limited to the current task's user-provided content and relevant structured context after explicit first-use consent; the service will not retain raw task data by default, and all returned advice will be validated locally and require user confirmation before application.

## Considered Options

- Send the full local task history and profile for maximum personalization.
- Define no contract until the AI phase and integrate directly into the UI.

## Consequences

The MVP remains offline-capable and testable, and model providers can change behind the contract. Personalization is intentionally limited to relevant, user-authorized context, and future AI work must implement consent, minimization, validation, and retention controls before it can be enabled.
