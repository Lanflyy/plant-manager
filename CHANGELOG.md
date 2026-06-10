# Changelog

All notable changes to the Plant Manager extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0] - 2026-05-26

### Added
- Auto-accept breeding feature for trusted users
- Commands to add/remove trusted auto-breed users by name or clicking
- Settings for auto-accept breeding (same level or higher plants)

## [1.2.0] - 2026-05-19

### Added
- Command to plant all seeds in a room (`:plants seed`)
- Switch on/off "can reproduce" attribute for all plants (`:plants canreproduce on/off`)
- Save settings in cache file for log level

### Fixed
- Abort retry get pet info when user typed abort
- Inexistent tooltip cleanup

## [1.1.0] - 2026-05-16

### Added
- JavaFX UI to visualize commands, logs, and setup settings
- Safety mechanism to treat plants only when they need to (can be bypassed in Settings → Advanced options)
- More visibility about treated plants, ignored dead plants, and plants with high well-being
- Dynamic logging level
- User notification for failed pet info retrieval
- Rate limiting between requests
- Advanced option to skip get pet info

### Changed
- Complete rewrite of the extension in Java code
- Refactored project structure into proper Java project
- Refactored code with Processor and Action pattern

### Fixed
- Issue where pets were considered as plants
- Issue about pets being removed from room not updating in memory
- Plant detection issues
- Plant to treat counter message

## [1.0.0] - 2026-05-12

### Added
- Initial release of Plant Manager extension
- Treat all living plants with `:plants` command
- Compost all dead plants with `:plants compost` command
- Dynamic logging level
- Basic plant management features

[1.3.0]: https://github.com/Lanflyy/plant-manager/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/Lanflyy/plant-manager/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/Lanflyy/plant-manager/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/Lanflyy/plant-manager/releases/tag/1.0.0