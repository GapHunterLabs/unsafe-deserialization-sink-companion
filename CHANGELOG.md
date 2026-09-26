<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Unsafe Deserialization Sink Companion Changelog

## [Unreleased]

## [0.1.1]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.1.0]

### Added

- Warning on `new ObjectInputStream(...).readObject()` inside a Spring
  MVC/JAX-RS endpoint method whose argument traces back to an
  untrusted parameter (same method, one direct hop) -- CWE-502,
  Deserialization of Untrusted Data.

[Unreleased]: https://github.com/GapHunterLabs/unsafe-deserialization-sink-companion/compare/0.1.1...HEAD
[0.1.1]: https://github.com/GapHunterLabs/unsafe-deserialization-sink-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/unsafe-deserialization-sink-companion/commits/0.1.0
