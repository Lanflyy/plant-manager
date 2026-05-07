# PlantManager

A browser extension for treating and composting monster plants in Habbo.

## Features

- Automatically detects and treats all monster plants in the current room
- Automatically detects and composts all dead monster plants in the current room
- Uses official gnode-api to communicate with the Habbo client

## Commands

`:plants` - Treat all plants
`:plants compost` - Compost all plants
`:plants abort` - Abort plant processing

## How to install (manually)

1. Install dependencies

```bash
npm install
```

2. Compile extension

```bash
npm run build
```

3. Run the extension

```bash
npm run test
```