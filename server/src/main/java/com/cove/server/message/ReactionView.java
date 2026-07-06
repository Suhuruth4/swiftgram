package com.cove.server.message;

import java.util.UUID;

public record ReactionView(UUID messageId, UUID userId, String reaction) {}
