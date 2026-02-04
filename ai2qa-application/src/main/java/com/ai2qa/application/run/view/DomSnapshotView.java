package com.ai2qa.application.run.view;

import java.time.Instant;

public record DomSnapshotView(
        String content,
        String url,
        String title,
        Instant capturedAt
) {
}
