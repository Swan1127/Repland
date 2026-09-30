# ADR 0003: Reusable event objects and track-based schedule instances

## Status

Accepted for the exploration build.

## Context

The day view needs the expressive power of an arrangement editor: a user should be able to create an event once, keep it reusable, and place it onto a parallel time track later. A task cannot be both a reusable object and a single immutable timeline rectangle. The week and month views also need to read the same placements without maintaining their own calendar state.

## Decision

Keep the task as the reusable event object. Represent every placement as a `PlannedSegment` carrying `date`, `startMinute`, `endMinute`, and a stable `trackId`. The day view may expose unplaced active tasks as an event shelf; dragging or placing one creates a new schedule instance and keeps the source task available for reuse. The week and month views are projections of those instances.

The first implementation uses a local UI name for newly added tracks and persists the stable track identifier on each segment. A later persistence pass may promote track metadata into a first-class table when users need cross-day track naming and ordering.

## Consequences

- Event identity, schedule placement, and execution evidence remain separable.
- Dragging does not force a task to become a one-off object.
- Multiple placements can be shown across tracks while task history remains unified.
- Plan writes must work even when no previous confirmed plan exists.
- Track metadata is intentionally the next evolution point; the current segment-level identifier is sufficient for the exploration loop but does not yet provide a global track library.

## Rejected alternatives

- Binding the event's date and time at creation: this makes reuse and drag placement awkward.
- Deriving every lane from overlap: it loses the user's chosen rhythm and makes the same track unstable between views.
- Maintaining separate day/week/month stores: it creates divergence and path dependency.
