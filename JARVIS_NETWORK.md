# JARVIS_NETWORK

`"Jarvis, tell John's Jarvis that I'm at the base."` → Jarvis A relays to
Jarvis B → John hears: "John, Greg's Jarvis asked me to let you know that
he's currently at the base." (Also `/jarvis tell <player> <message>`.)

## Rules

- Only **explicitly transmitted text** crosses instances. Private memory,
  knowledge and weights are never readable across the boundary.
- Master switch `jarvisToJarvis` (common config); per-message priorities.
- **Offline delivery**: messages queue in memory and drain on login with a
  "pending messages" notice. Delivery confirmations return to the sender.
- Name resolution is case-insensitive against online players; unknown names
  get an honest "couldn't find" reply, never a fabricated delivery.
