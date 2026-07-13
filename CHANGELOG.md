# Changelog

## 0.1.3

- Refreshed the mobile tab panel immediately after tab state changes.
- Increased the close target to 48 dp and added swipe-to-close.
- Added a dimmed scrim and outside-tap dismissal for the mobile sidebar.
- Unified new-tab behavior and hid `about:blank` from the address field.
- Limited the fixed sidebar to tablets instead of landscape phones.
- Improved workspace creation validation and focus.
- Replaced the launcher artwork with an inverted Zen logo variant.
- Added stable CI signing through encrypted GitHub Actions secrets.
- Bumped Android versionCode to 4.

## 0.1.2

- Added Android safe-area handling through WindowInsets.
- Moved controls away from status bars, gesture navigation and display cutouts.
- Added launcher icon resources.
- Bumped Android versionCode to 3.

## 0.1.1

- Fixed GeckoView/GeckoSession attachment lifecycle during configuration changes.
- Avoided redundant UI reconstruction on HyperOS startup configuration callbacks.
- Removed eager GeckoRuntime warm-up and switched to lazy initialization.
- Added debug lifecycle and Gecko logging.
- Bumped Android versionCode to 2.

## 0.1.0

- Initial GeckoView browser prototype with tabs and workspaces.
