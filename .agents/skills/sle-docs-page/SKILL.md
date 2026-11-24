---
name: sle-docs-page
description: Add or edit a page in SLE's offline HTML guide under docs/guide. Use for tutorial, feature reference, navigation, and screenshot changes in that guide.
---

# Maintain the SLE HTML guide

Read `docs/DOCS_MAINTENANCE.md`, the affected guide pages and their neighbors, and the relevant rows in `docs/plans/coverage.md`. Verify changed claims against runnable entry points or current code. Record the build and actual results when checking editor workflows on Windows.

- Reuse an adjacent page's layout and `docs/guide/styles.css`. Use lowercase, hyphenated filenames for new pages and relative links that work over file and HTTP.
- Preserve public page URLs and existing heading IDs. Check incoming links before editing, including links from the coverage inventory and feature index.
- Update shared chapter navigation and adjacent Previous and Next links when adding or moving a chapter. Keep feature index entries and affected coverage rows aligned with their guide anchors. Keep the index links usable with JavaScript disabled.
- For requested screenshots, record the PNG filename, exact UI state, alt text, and any capture blocker in the coverage inventory. Capture the relevant editor window when available and store the image in `docs/guide/images/`. Use a figure and caption that help readers locate the relevant controls. Keep labels, values, and expected UI states in the article text even when a capture is unavailable.
- Run `python3 docs/guide/check_links.py` from the repository root and inspect the changed HTML, navigation, and inventory diff. For a new page, check an incoming and outgoing link. For an edit, check the existing URL and fragments still resolve.
