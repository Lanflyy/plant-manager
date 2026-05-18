# Plants

A Java G-Earth extension for treating and composting monster plants in the current room.

## Commands

* `:plants` - Treat all living plants
* `:plants compost` - Compost all dead plants
* `:plants seed` - Plant seeds for all dead plants in the room
* `:plants canreproduce on` - Enable can reproduce on all your plants
* `:plants canreproduce off` - Disable can reproduce on all your plants
* `:plants abort` - Abort plant processing

## Build

```bash
mvn -DskipTests clean package
```

The built extension jar is created in `target/bin`.
