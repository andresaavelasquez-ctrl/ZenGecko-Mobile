# Changelog

## 0.1.2

- Added safe-area handling for status bars, navigation bars and display cutouts.
- Reapplied insets after rotations and compact/wide layout changes.
- Kept the mobile sidebar inside the safe display area.
- Improved address-bar focus and soft-keyboard activation.
- Added an unofficial dark launcher icon based on Zen Browser's public visual identity.
- Ignored local `.zengecko-backups/` directories.
- Bumped Android versionCode to 3.

## 0.1.1

- Fixed GeckoView/GeckoSession attachment lifecycle during configuration changes.
- Avoided redundant UI reconstruction on HyperOS startup configuration callbacks.
- Removed eager GeckoRuntime warm-up and switched to lazy initialization.
- Added debug lifecycle and Gecko logging.
- Bumped Android versionCode to 2.

## 0.1.0

- Initial GeckoView browser prototype with tabs and workspaces.
