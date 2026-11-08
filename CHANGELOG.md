# Changelog

All notable changes to the Plant Manager extension will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
 
 ## [1.6.0] - 2026-11-08
 ### Added
 * Feature : Localize the whole extension depending on what G-Earth sent as language to Plant extension (defaulting to English)
 * Feature : Upgrade to JDK 21
 
 ### Fixed
 * Bugfix : Fix a crash (NullPointerException) being thrown sometimes on extension start.
 
 ## [1.5.1] - 2026-11-08
 ### Fixed
 * Bugfix : Seed functionality works now for non tradable monsterplant seeds.
 
 ## [1.5.0] - 2026-11-08
 ### Added
 * Upgrade into G-Earth-API 1.5.4 (but keeping JDK 8 for now)
 
 ### Fixed
 * Bugfix : Huge downsize of the jar from ~20Mo => ~2Mo
 
 ## [1.4.2] - 2026-11-08
 ### Fixed
 * Bugfix : Fix Furni Data cache mechanism with a mixture of max-age and hash file
 
 ## [1.4.1] - 2026-11-08
 ### Added
 * Feature : Improve copy accessibility
 
 ### Fixed
 * Bugfix : "Do not check pet info" saved now in cache setting (when reopening the extension)
 * Bugfix : Block received pet info when expecting it to now pop the plant UI during the treat process
 
 ## [1.4.0] - 2026-11-08
 ### Added
 * Feature : Major UI rework
 
 ### Fixed
 * Bugfix : Detect Bulk added items (currently used for temporary items)
 * Bugfix : Add loading of Furni Data (with a cache detection) to fix seed class id for hotels other than fr

## [1.3.3] - 2026-06-24
### Fixed
* Bugfix: Entities and room items were hold in memory after moving to another room, causing on rare cases the extension to still considering old values when user is on a new room.

## [1.3.2] - 2026-06-17
### Fixed
* Bugfix: PetStatusUpdate packet was not updating `isDead` plant status in memory, causing plants detected as alive and not possible to compost when entering a room with a plant that became dead, or when a plant becomes dead on real-time.
* Bugfix: Warning about untracked entity pet info was showing on the log because of removal occurring 2 times.

## [1.3.1] - 2026-06-11

### Added
- Feature : Add command to count the number of plants that can breed
- Feature : Add accept auto-breed from any user option

### Changed
- Make message "start can reproduction switch on/off" notification more clear for the user
- Move getOwnerId in PlantUtils

### Fixed
- Bugfix: Update in-memory "can breed" status after PetStatusUpdate packet received
- Bugfix: Can reproduce switch on/off applied now only to plants that can actually breed
- Bugfix: Compost command processes now only owned plants
- Bugfix: Too long wait when trying to compost plants inside a room full of alive plants
- Bugfix: Make message more informative about plants to be composted

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

[1.3.3]: https://github.com/Lanflyy/plant-manager/compare/1.3.2...1.3.3
[1.3.2]: https://github.com/Lanflyy/plant-manager/compare/1.3.1...1.3.2
[1.3.1]: https://github.com/Lanflyy/plant-manager/compare/1.3.0...1.3.1
[1.3.0]: https://github.com/Lanflyy/plant-manager/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/Lanflyy/plant-manager/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/Lanflyy/plant-manager/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/Lanflyy/plant-manager/releases/tag/1.0.0