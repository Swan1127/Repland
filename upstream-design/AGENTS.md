# Active Repland development version

This directory contains the primary development version designated by the user on 2026-10-22.
Work on this version independently. Integration with the separate sibling `../android/` project is deferred; do not copy or merge its product code without a new user request.

Source of truth: `repland/`. The directory `app/src/main/java/com/swan1127/repland` is a junction to that source directory, not a second source tree. Run `setup-junction.ps1` after extracting a fresh checkout.

Use JDK 21 and the Gradle 9.1.0 wrapper. Keep model requests opt-in and use synthetic data for network tests. Never commit model credentials or personal databases.

Run the JVM test suite and Debug/Release builds after behavior changes. Run connected tests only on a dedicated test emulator; never clear or replace a user's installed app data.

The locally supplied timetable at `app/src/androidTest/assets/user-timetable.pdf` and its `user-timetable.expected.txt` manifest are ignored private test fixtures. Do not include them, extracted course schedules, or verification exports in shared source archives or APKs. PDF-specific tests must skip explicitly when their required fixtures are absent. The public XLSX regression uses synthetic courses.

See `docs/REPAIR_AND_TEST_2026-10-22.md` for repair and verification results, and `docs/ACCEPTANCE_MATRIX_2026-10-22.md` for the test matrix and limits.
