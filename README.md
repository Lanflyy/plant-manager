# Plants

A Java G-Earth extension for treating and composting monster plants in the current room.

## Commands

* `:plants` - Treat all living plants
* `:plants compost` - Compost all dead plants
* `:plants seed` - Plant every seed in the current room.
* `:plants canreproduce on` - Enable "Can Reproduce" for all plants in the current room.
* `:plants canreproduce off` - Disable "Can Reproduce" for all plants in the current room.
* `:plants countcanbreed` - Counts how many plants in the current room can breed (even for not owned rooms).
* `:plants autobreed on` - Enable auto-accept breeding (check Settings).
* `:plants autobreed off` - Disable auto-accept breeding.
* `:plants autobreed adduser NAME` - Add a trusted auto-breed user.
* `:plants autobreed adduser` - Add a trusted auto-breed user by clicking them.
* `:plants autobreed removeuser NAME` - Remove a trusted auto-breed user.
* `:plants autobreed removeuser` - Remove a trusted auto-breed user by clicking them.
* `:plants abort` - Stop any current processing plant action.

## Build

```bash
mvn -DskipTests clean package
```

The built extension jar is created in `target/bin`.
