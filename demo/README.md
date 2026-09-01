# Demo data for screenshots

`UploadController.java` — `handleUpload`/`handleUpload2` flagged
(both deserialize the untrusted `payload` parameter); `handleInternal`
not flagged (deserializes a trusted internal constant).

## How to get the screenshot

1. `./gradlew runIde` from `unsafe-deserialization-sink-companion`,
   open this `demo/` folder as the project.
2. Full Screen, open `UploadController.java` — warnings should appear
   on the two `new ObjectInputStream(...)` constructions but not on
   the third.
3. Screenshot with all three methods visible, save into
   `unsafe-deserialization-sink-companion/docs/screenshots/`. Close the
   sandbox.
