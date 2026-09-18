# Repland

Repland is a Chinese-language, single-device task-planning app for personal MVP testing. It turns user-entered tasks and unavailable time into a user-confirmed, actionable plan.

## Product scope

**MVP**:
The first product release containing the task-planning loop: task entry, AI-assisted planning through the Agent workflow, deterministic fallback planning, daily execution feedback, and plan adjustment. It excludes account-based sync.
_Avoid_: Public release, complete product

**Local data**:
The tasks, constraints, feedback, plans, and history stored on the user's current device. It is not automatically synchronized, but can be exported as JSON by the user.
_Avoid_: Cloud account, cross-device data

**AI suggestion**:
A non-binding AI recommendation that may analyse a task or propose a planning adjustment through the product's API-connected workflow. It never changes a task's status or applies a plan without user confirmation.
_Avoid_: Automatic execution, autonomous planning

**AI decision authorization**:
A user-facing option available when a planning conflict has multiple acceptable trade-offs. If the user explicitly chooses “由 AI 决定”, Repland may apply the AI's choice within that current decision scope and records it as a new, reversible plan version; this does not enable permanent autonomous planning.
_Avoid_: Implied authorization, permanent auto-planning

**Task title**:
The user-visible name of a task, created when the task is saved. If the user provides a title, it is retained; if the user provides only a task description, AI may generate an editable title from that description.
_Avoid_: AI-overwritten title, inferred title without task description

**Task description**:
The user's description of what needs to be done, including optional context such as an expected total duration. It is the primary user-provided context for AI task understanding.
_Avoid_: Mandatory detailed specification, actual execution result

**Dependency note**:
An optional ordering statement in a task description, such as “finish A before B”. In the MVP, AI may identify it and propose an order, but the dependency is not a separate enforced object until the user confirms the resulting plan.
_Avoid_: Hidden hard dependency, full dependency graph in MVP

**Task creation minimum**:
A task can be saved with a user-provided title or description, a category, and an initial priority. Deadline, expected completion days, and expected total duration are optional additions; missing time information may keep the task pending for automatic time placement.
_Avoid_: Required detailed planning form, rejected task without optional fields

**Category preference settings**:
The MVP uses four preset categories—课内学习, 课外学习, 办公事务, and 休闲娱乐—with default relative preferences of 30%, 25%, 25%, and 20%. The user may adjust their relative weights, but does not add custom categories in the MVP.
_Avoid_: Fixed universal category order, unrestricted category taxonomy

**Task-save planning**:
When a task is saved, Repland may immediately analyse it and produce a planning draft or advice. The result is always reviewable and requires user confirmation before becoming the current plan.
_Avoid_: Applying an AI plan on save, waiting for a complete form

**Plan draft**:
A generated planning proposal that has not yet become the current plan. It has two outputs: a task list ordered for execution and, when enough time information exists, a plan with dated time segments. Tasks without enough time information can still participate in basic list ordering but remain pending for exact placement. It can be reviewed, edited, accepted, or discarded by the user.
_Avoid_: Applied plan, automatic change

**Plan confirmation**:
The user action that turns a draft into the current plan. The user may accept the whole draft or edit selected parts before confirming; the confirmed result becomes a new plan version.
Selecting “由 AI 决定” for a current conflict is an explicit authorization and may count as confirmation for that decision scope, allowing AI to apply the choice directly while recording a reversible new version.
_Avoid_: Passive approval, automatic application without authorization

**Authorization scope**:
The specific conflict or planning decision covered by the user's “由 AI 决定” choice. It may permit AI to adjust a directly affected manual order, but does not grant permission to change unrelated tasks, statuses, or global settings.
_Avoid_: Unlimited AI authority, authorization spillover

**Planning setting change**:
A change to a global preference such as category weights or availability rules. It becomes planning input for a new draft rather than immediately mutating the current plan; the user confirms the resulting version before it takes effect.
_Avoid_: Immediate bulk mutation, history loss

**Current plan**:
The single user-confirmed plan currently used for execution and reminders. New AI results remain drafts until confirmed, and historical versions remain available for rollback; the MVP does not maintain multiple simultaneously active plans.
If an unconfirmed draft exists, the current plan still governs execution, notifications, and task state.
_Avoid_: Competing active plans, draft treated as current

**Unconfirmed draft**:
A newly generated plan adjustment that has not been accepted by the user. The existing current plan continues to govern execution and reminders until the draft is accepted, discarded, or edited and accepted.
_Avoid_: Silent replacement, notification from draft

**List-only plan state**:
The valid planning state in which tasks are ordered in the task list but have no concrete time segments because availability or duration information is insufficient. The task list remains usable while exact time placement is deferred.
_Avoid_: Empty plan, assumed availability

**Expected total duration**:
The user's estimate of how much working time a task may require, optionally added as part of the task description. It can support planning but is not treated as the task's actual duration or as a direct priority factor.
_Avoid_: Actual duration, guaranteed estimate, required duration

**Actual total duration**:
The time the user actually spends completing or attempting a task, recorded through execution feedback. It does not directly affect task ranking or task importance, but may inform later user-profile judgments and improve future duration, split, or focus recommendations.
_Avoid_: Expected duration, automatic measurement

**Deadline**:
The local calendar date by which the user wants a task finished; unless the user changes it, the deadline ends at 23:59 on that date.
_Avoid_: Exact timestamp, due moment

**Overdue task**:
A task whose deadline has passed while it remains unfinished. It stays active until the user chooses a new plan direction, such as postponing, splitting, lowering the goal, changing the approach, or cancelling it; the system does not cancel it automatically.
_Avoid_: Automatic cancellation, hidden deadline extension

**Plan feasibility warning**:
A user-visible statement that the currently available time cannot satisfy all planned work before its deadlines. AI may explain the shortfall and recommend trade-offs such as rotation, postponement, splitting, lowering the goal, or abandonment; the user chooses or authorizes the outcome rather than the system silently changing or dropping work.
_Avoid_: Automatic failure, hidden rescheduling

**Manual plan adjustment**:
A user-confirmed reorder or placement change for the current plan. It expresses the user's current intent and should be preserved during later replanning unless the user releases it; it does not silently rewrite the task's initial priority.
_Avoid_: Permanent priority rewrite, ignored user intent

**Manual order override**:
The user's highest-priority direct change to the current plan, made by long-pressing a task and dragging it up or down. On a concrete time-segment plan, the drag also changes the corresponding time-segment arrangement. It takes precedence over AI ordering for the current plan; the end-of-day workflow records the user's final ordered table for later analysis and history.
_Avoid_: AI undoing the order, unrecorded manual decision

**Manual-order behavior evidence**:
The user's repeated direct ordering choices may inform later AI suggestions about planning preferences. They do not silently rewrite initial priorities, category weights, or the user's task data.
_Avoid_: Hidden preference mutation, one-time drag treated as permanent rule

**Manual-order feasibility warning**:
A non-blocking warning shown when the user's direct ordering may cause a deadline or capacity problem. It informs the user but cannot prevent or reverse the manual order.
_Avoid_: Blocking user control, silent reorder

**Manual parallel override**:
A user-created overlap between a fixed task and a normal task. AI must preserve the user choice and may warn about execution risk, but cannot generate or silently remove the overlap.
_Avoid_: AI-generated concurrency, automatic conflict correction

**Initial-priority change**:
A user edit to a task's original high/medium/low priority, which remains a separate user judgment from dynamic priority. It triggers recalculation and a new adjustment draft rather than silently changing the current plan.
_Avoid_: Immediate plan mutation, dynamic value rewritten as initial value

**Locked time block**:
A scheduled task interval that the user has protected from automatic movement. Only the user can unlock or change it. AI treats it as occupied and never generates an overlapping task placement; the user may manually create an overlap when they intentionally want parallel work.
_Avoid_: Locked task, immutable task

**Fixed task**:
A normal task marked with the user's “不可避免” priority. Its confirmed time placement is treated as fixed for AI planning, but it is not a separate event object. The user may manually place a normal task in the same interval for parallel work; AI does not generate such overlap.
_Avoid_: Separate event model, AI-generated overlap

**Execution log**:
A record of a user-confirmed task status or feedback event. It remains historically true even when a plan version is later rolled back. If the user corrects a past entry, the original event and the correction are both retained.
_Avoid_: Rewritten history, inferred completion

**Plan version**:
A saved snapshot of task ordering, scheduled intervals, and task statuses at a confirmed point in time. Rolling back restores that snapshot as a new current version instead of deleting the prior version or its later execution logs.
_Avoid_: Overwritten plan, temporary draft

**Task status**:
The user's current execution state for a task: not started, in progress, completed, postponed, cancelled, or replaced. When a confirmed time segment begins, the task may automatically enter “进行中”, and the user can manually change it. Completion, postponement, cancellation, replacement, and end-of-day outcomes still require the user's action; if no feedback is submitted, the system does not infer them.
_Avoid_: AI status, automatically completed

**Automatic-start event**:
The system event generated when a confirmed time segment begins and moves its task into “进行中”. It is recorded as an execution-plan event, not as evidence that the task was completed; later user corrections are appended.
_Avoid_: Automatic completion, automatic postponement

**Task-segment status distinction**:
A daily work segment's completed or postponed result is recorded independently from the overall task status. Completing one segment does not automatically complete the task; the user controls the task-level outcome and total progress.
_Avoid_: Segment completion equals task completion, inferred total progress

**Cancelled task**:
A user-confirmed task outcome that removes a task from future planning without deleting its history. A cancelled task remains available in plan versions, execution logs, and relevant statistics and may be restored by the user.
_Avoid_: Hard deletion, AI cancellation

**Task restoration**:
A user action that returns a completed or cancelled task to active planning. Restoration preserves the earlier completion or cancellation record and starts a new planning analysis or draft rather than overwriting history.
_Avoid_: Erased outcome, automatic resurrection

**Planning quantum**:
A 30-minute unit used by the MVP to place work into available time. A task's supplied duration is rounded up to whole quanta for automatic scheduling.
_Avoid_: Exact productivity measure, fixed appointment length

**Recurring time template**:
A weekly repeating course or rest interval that blocks planning on matching days. A date-specific override may change that day's availability.
_Avoid_: Imported calendar, one-off event

**Recurring task scope**:
The MVP does not provide a dedicated recurring-task template. Repeated work is represented by user-created tasks while the need is being validated; recurring course and rest availability remain separate supported templates.
_Avoid_: Habit automation in MVP, recurring-task confusion

**Pending task**:
A task that is visible in the task list and can still receive basic ordering, but lacks enough time information for exact automatic placement, such as an expected duration or necessary time constraint. A task with a duration but no deadline may still be placed within the rolling planning horizon. It remains user-controlled and is not silently discarded.
_Avoid_: Invalid task, ignored task

**Deterministic fallback priority**:
The MVP's explainable priority calculation used when no AI is available. AI-specific factors receive neutral scores while user priority, category preference, deadline or overdue state, and postponements drive ordering with stable tie-breaking. Execution-time proximity is presented by the timed execution view and is not an independent fallback-ranking factor.
_Avoid_: Machine-learned ranking, random order

**Plan regeneration trigger**:
A user-visible analysis or draft-generation event caused by saving a task, generating a plan, submitting execution feedback, changing a constraint, or explicitly requesting replanning. Saving a task may trigger immediate analysis. It creates advice or a proposal but never applies a plan automatically.
_Avoid_: Silent reschedule, background mutation

**Future-constraint change**:
A user change to a deadline, expected cycle, category, priority, timetable, rest template, or other planning input. It preserves already elapsed dates and historical versions, and generates a proposal for affected future work rather than silently rewriting the current plan.
_Avoid_: Rewriting history, retroactive schedule mutation

**Dynamic priority**:
The changing priority shown for ordering tasks in the plan. Its soft-ranking structure is currently 25% user initial priority, 15% category preference, 45% AI task-planning suitability, and 15% AI context judgment. The 15% AI context judgment includes deadline proximity, accumulated postponements, the general profile, the learning profile, course/grade impact, and other relevant context. Hard constraints and feasibility rules are handled before or outside this score; the user's initial priority remains visible and is not silently rewritten.
_Avoid_: Overwritten user priority, fixed lifetime score

**Execution-time proximity**:
The fact that a task's scheduled time segment is about to begin. In the current product direction it does not independently raise dynamic priority; the time-segment execution view presents tasks by their scheduled rows instead.
_Avoid_: Extra ranking factor, urgency score from clock proximity

**Priority presentation**:
The user's initial high/medium/low priority remains stored and visible in task details. The task list and plan primarily show the current dynamic priority used for ordering, so the displayed planning urgency can change without rewriting the original user judgment.
_Avoid_: Confusing dynamic urgency with initial priority, hidden rewrite

**Structured AI assessment**:
An AI response that reports task judgments such as difficulty, expected work pattern, context importance, suggested duration or split, postponement risk, and reasons in a form the planner can use. The user's general profile and learning profile provide the AI's context judgment, which is a 15% factor in dynamic-priority decisions. It can recommend a capacity trade-off and help reassess a task's goal after repeated postponement. Internal ranking scores remain backend data; the user sees a short reason and may expand it for detailed reasons. It informs planning but is not a task-status command or a rewrite of the user's initial priority.
_Avoid_: Opaque score, autonomous status change

**User correction of AI assessment**:
Any user edit to an AI-suggested task description, duration, split, execution cycle, or goal context. The correction is treated as user-provided planning evidence and takes precedence over the earlier AI suggestion in later analysis.
_Avoid_: AI suggestion treated as fact, uneditable judgment

**Repeated postponement**:
Each user-confirmed postponement raises a task's dynamic urgency. After two cumulative postponements, Repland should prompt the user to reassess the task's goal or approach; the prompt is advice and does not change the task automatically.
_Avoid_: Infinite priority escalation, automatic goal change

**Local reminder**:
A device-scheduled notification for a confirmed plan, limited in the MVP to a task's start time when it has a confirmed concrete time segment, a near-deadline warning when a deadline exists, and the end-of-day review. A task without a deadline does not receive a deadline reminder, and a task without a concrete time segment does not receive a start reminder. It is optional, one-time per trigger, and uses the device's local time.
_Avoid_: Cloud push, mandatory alarm

**First-use setup**:
The user may create a first task and receive a basic plan without first configuring a timetable or rest template. Those availability settings are optional inputs that improve exact time placement.
Without configured availability, the MVP provides the task list but does not assume the user is free or generate a reliable exact time-slot plan.
_Avoid_: Setup wall, unusable empty state, assumed free time

**Completion cleanup**:
When the user marks a task completed, future unexecuted work segments are removed from the active plan while remaining in plan history and execution records. Any resulting reallocation is proposed as a draft rather than silently applied.
_Avoid_: Deleted history, inferred completion

**Daily review**:
An optional end-of-day summary covering completed and unfinished work, actual time, progress, learning results when relevant, AI interpretation, and next-day adjustment advice. Skipping it does not allow the system to infer task status.
_Avoid_: Mandatory diary, automatic status inference

**End-of-day final-plan record**:
The record produced at the end of the day from the user's final manually adjusted plan table. It preserves the final order and relevant plan state for subsequent AI analysis and historical review, without changing the user's task statuses by inference.
_Avoid_: AI-generated final order, unrecorded manual arrangement

**End-of-day snapshot**:
An automatically retained record of the final plan table for that day. It supports history and later analysis but does not bypass confirmation to become a new future current plan.
_Avoid_: Hidden future-plan mutation, unconfirmed active plan

**Profile aggregation**:
The conversion of older detailed execution evidence into durable general-profile or learning-profile patterns after the detailed daily log window expires. Aggregation keeps useful planning evidence while allowing old raw status changes to be pruned or hidden.
_Avoid_: Permanent raw diary, discarded learning evidence

**Planning horizon**:
The rolling 30-day window in which the MVP expands tasks into daily work. Tasks beyond that window remain stored without being rendered as daily items.
_Avoid_: Project end date, permanent schedule

**Daily work segment**:
A dated, timed portion of one task's work. A multi-day task has one task identity and may have many daily work segments carrying its remaining progress. AI-generated duration and split suggestions become exact time placement only after user confirmation; the user can edit, merge, or further split segments.
_Avoid_: Duplicated task, separate task

**Timed execution view**:
The view for a confirmed plan with concrete time segments. It displays scheduled tasks as equal-width rows, with a time axis on the left for reference; each task segment is represented by its start and end time points. After a segment ends, that row leaves this view and becomes available in the past-segment review view.
_Avoid_: Permanent task list, AI-marked completion

**Task time label**:
The concrete time representation shown for a scheduled task segment, using two points such as “09:00–10:00” for start and end rather than using task-card width to encode duration.
_Avoid_: Duration encoded by width, continuous chart required

**Past-segment review view**:
The horizontally reachable view for ended time segments. The user reviews each ended segment and records whether the work was completed or postponed; the system and AI do not infer that status.
_Avoid_: Automatic completion, hidden unfinished work

**Pending past segment**:
An ended time segment that the user has not yet reviewed. It remains marked “待确认” and does not count as completed or postponed until the user submits a status.
_Avoid_: Silent failure, automatic postponement

**Task detail feedback**:
The detail interaction available from a past time segment or the normal plan. The user can add actual time, progress, completed content, results, or other feedback; these inputs inform later planning and profile updates without allowing AI to set the task status.
_Avoid_: Mandatory long form, AI-written execution result

**Batch status edit**:
A user action that selects multiple tasks and applies the same status change together. Each selected task receives its own execution record and any resulting planning analysis; batch editing does not allow AI or the system to infer the statuses.
_Avoid_: Bulk inferred status, merged task identity

**Task replacement**:
A user choice from task details to turn the current task into a new task. The original task receives the “已替换” status and moves to today's past-task record with its history and feedback, while the replacement task receives a new task identity, may reuse selected description, goal context, or feedback, and enters a new planning analysis.
_Avoid_: Overwriting task identity, erased original task

**Execution feedback**:
The user's report about what was completed, the actual progress or time, and any result after attempting a task. It is evidence for later advice, not an automatic status change.
_Avoid_: Automatic completion, inferred outcome

**Postponement feedback**:
A user-confirmed decision that a past time segment was not completed and should be postponed. It records the event, increases the relevant dynamic urgency, and triggers an adjustment draft; it does not silently move the task to a new date.
_Avoid_: Automatic rollover, unrecorded postponement

**Postponement reason**:
An optional explanation attached to a user-confirmed postponement, such as insufficient time, unexpected difficulty, or an interruption. It can inform later AI analysis and profile updates but is never required to record the postponement.
_Avoid_: Mandatory justification, moral judgment

**Partial completion adjustment**:
The replanning proposal created after the user reports that only part of a task was completed. It recalculates the remaining work and may carry it into later dates, but requires confirmation and does not infer or change the user's task status.
_Avoid_: Lost remaining work, automatic completion

**User planning cycle**:
The user's expected number of days for completing a task. It is the primary planning cycle when supplied; AI may suggest a different cycle, but the suggestion does not replace the user's value unless the user changes it.
_Avoid_: AI-imposed deadline, fixed universal cycle

**Profile update**:
The gradual update of the general profile and learning profile from user-confirmed completion, actual time, progress, results, and relevant grades. AI may propose conclusions, but the user can inspect, correct, or delete inaccurate conclusions.
_Avoid_: Hidden permanent label, AI-only identity

**Goal reassessment**:
The user-facing review prompted after repeated postponement. It offers choices such as maintaining the goal, splitting the task, lowering the goal, moving the deadline, changing the approach, or cancelling the task; AI may recommend but does not change the goal without authorization.
_Avoid_: Automatic goal reduction, shame-oriented reminder

**Long-term goal context**:
Optional goal background attached to a task in the MVP, usually supplied in the task description or identified by AI. It helps the AI judge context relevance without creating a standalone long-term planning module; that module is reserved for a later version.
_Avoid_: Full goal hierarchy in MVP, hidden goal inference

**Global planning pass**:
The planning stage that considers all unfinished tasks together before assigning daily dates and time segments. It uses constraints, available time, task splitting, and dynamic priority so that local daily ordering does not undermine later deadlines.
_Avoid_: Independent per-day sorting, local optimum only

**New-task conflict draft**:
When a task is added to an existing plan, the task is first retained in the task list and the existing plan is preserved. If capacity or time conflicts appear, Repland creates an adjustment draft that explains the conflict; the user may edit it or explicitly authorize AI to decide.
_Avoid_: Silent displacement, discarded new task

**AI degraded mode**:
The local fallback mode used when the AI API is unavailable or returns unusable data. It continues basic explainable ordering and plan generation, treats AI-specific judgments as neutral, and tells the user that advanced AI assessment is unavailable.
_Avoid_: Broken core loop, fabricated AI judgment

**Date-specific override**:
A user-entered change to one occurrence of a weekly course or rest template. It changes that date's availability without rewriting the recurring template.
_Avoid_: Permanent template edit, imported calendar event

**One-day availability override**:
A user-entered availability change for a particular date. It takes precedence over the recurring availability template for that date only and becomes input to a future-plan adjustment draft.
_Avoid_: Permanent template rewrite, retroactive history change

**Local timetable import**:
The MVP capability to read and load a user-provided timetable on the device and turn courses into fixed planning constraints. The user confirms or corrects the imported result; external academic-system synchronization is outside the MVP.
_Avoid_: Unverified course schedule, external sync dependency

**Habit-formation flow**:
A later feature flow that uses execution feedback and profile data to provide active habit-building guidance. The MVP collects the supporting evidence through planning, execution, and review but does not include the complete habit-coaching mechanism.
_Avoid_: Mandatory coaching, MVP behavior intervention

**MVP validation**:
The evaluation of whether users can repeatedly complete the low-cost loop of adding tasks, receiving a plan, executing, giving feedback, and adjusting the plan. Core measures include plan confirmation, task completion, user-initiated adjustment, and continued use before adding long-term planning or full habit formation.
_Avoid_: Feature-count completion, AI quality judged in isolation

**Local data reset**:
An explicit user action that clears the app's locally stored tasks, plans, logs, and settings after confirmation. Previously exported JSON files are outside the reset scope.
_Avoid_: Account deletion, remote wipe

**AI contract**:
A versioned, typed boundary through which Repland may request task analysis, priority advice, split suggestions, replan proposals, or daily summaries. Its responses are advisory data and never authoritative task-state commands. The MVP exposes these results through the Agent's structured workflow rather than requiring a separate free-form AI chat page.
_Avoid_: Free-form chat protocol, autonomous action

**Model-provider abstraction**:
The product boundary that hides the specific AI model and service provider from users. The Agent workflow and AI contract remain stable while the implementation may change providers or models.
_Avoid_: Provider-dependent user experience, model choice as core workflow

**AI data boundary**:
The rule that only the current task's user-provided content and necessary, relevant structured history may leave the device after explicit user consent. Unrelated local data and raw history are not sent or retained by default.
_Avoid_: Full-device upload, silent profiling

**AI consent**:
The user's explicit permission before task content and necessary profile context are sent to the API-connected AI workflow. The user can disable AI later; local deterministic planning remains available.
_Avoid_: Silent upload, AI-required operation

## GrillMe continuation status

The planned interview frontier has been completed through the current round. The main product decisions, task lifecycle rules, user-authority boundaries, AI workflow, V1 scope, and consistency corrections are recorded above. The next step is to produce the complete product-thinking summary when the user requests it.
