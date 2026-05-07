import { Extension, HDirection, HEntity, HEntityType, HPacket, HMessage } from 'gnode-api'
import { readFileSync } from 'fs'

const packageJson = JSON.parse(readFileSync('./package.json', 'utf8'))

const extension = new Extension(packageJson)
extension.run()

// State management
let isProcessing: boolean = false
let isInitialized: boolean = false
let currentRoomId: number = -1
const plants: Map<number, boolean> = new Map()

// Commands configuration
const COMMANDS = {
  TREAT: ':plants',
  COMPOST: ':plants compost',
  ABORT: ':plants abort'
} as const

const sleep = (ms: number): Promise<void> => new Promise(resolve => setTimeout(resolve, ms))

const sendSystemMessage = (message: string): void => {
  const packet = new HPacket('Whisper', HDirection.TOCLIENT)
  packet.appendInt(1)
  packet.appendString(message)
  packet.appendInt(0)
  packet.appendInt(34)
  packet.appendInt(0)
  packet.appendInt(-1)
  extension.sendToClient(packet)
}

const handleUsers = (message: HMessage): void => {
  const packet = message.getPacket()
  try {
    packet.resetReadIndex() // Ensure we read from the beginning
    const parsedEntities = HEntity.parse(packet)

    let addedCount = 0
    let otherTypes = new Set<number>()

    parsedEntities.forEach((entity: HEntity) => {
      if (entity.entityType === HEntityType.PET) {
        const isDead = entity.stuff[8] === true
        plants.set(entity.id, isDead)
        addedCount++
      } else {
        otherTypes.add(entity.entityType)
      }
    })

    isInitialized = true // Room has successfully sent its entities
    console.log(`[handleUsers] Parsed ${parsedEntities.length} entities. Added ${addedCount} pets. Total plants in memory: ${plants.size}`)
  } catch (err) {
    console.error('[handleUsers] Failed to parse entities:', err)
  }
}

const processPlants = async (actionType: 'treat' | 'compost'): Promise<void> => {
  let count = 0

  for (const [plantId, isDead] of plants.entries()) {
    if (!isProcessing) break // Processing aborted

    const shouldCompost = actionType === 'compost' && isDead
    const shouldTreat = actionType === 'treat' && !isDead

    if (shouldCompost || shouldTreat) {
      const packetName = shouldCompost ? 'CompostPlant' : 'RespectPet'
      const packet = new HPacket(packetName, HDirection.TOSERVER)
      packet.appendInt(plantId)

      try {
        extension.sendToServer(packet)
        count++
        await sleep(600)
      } catch (err) {
        console.error(`Failed to send ${packetName} for plant ${plantId}:`, err)
      }
    }
  }

  if (isProcessing) {
    const actionName = actionType === 'compost' ? 'compost' : 'treated'
    sendSystemMessage(`All plants have been ${actionName} (${count})`)
    isProcessing = false
  } else {
    sendSystemMessage('Plant processing aborted.')
  }
}

const handleChat = (message: HMessage): void => {
  const packet = message.getPacket()
  const text = packet.readString()

  const isCommand = Object.values(COMMANDS).includes(text as any)

  if (isCommand) {
    message.blocked = true

    if (!isInitialized) {
      sendSystemMessage('Error: Room not initialized yet! Please wait for the room to load or reload it.')
      return
    }
  }

  if (text === COMMANDS.TREAT) {
    isProcessing = true
    sendSystemMessage(`Treating plants started... (Found ${plants.size} plants)`)
    console.log(`Command TREAT executed. Plants in memory: ${plants.size}`)
    processPlants('treat')
  } else if (text === COMMANDS.COMPOST) {
    isProcessing = true
    sendSystemMessage(`Composting plants started... (Found ${plants.size} plants)`)
    console.log(`Command COMPOST executed. Plants in memory: ${plants.size}`)
    processPlants('compost')
  } else if (text === COMMANDS.ABORT) {
    isProcessing = false
    console.log(`Command ABORT executed.`)
  }
}

const handleGetGuestRoom = (message: HMessage): void => {
  const packet = message.getPacket()
  // FIX: packet.readInteger() is the correct gnode-api method for reading 32-bit integers
  const roomId = packet.readInteger()
  const param2 = packet.readInteger()

  // param2 is 0 on the initial room request, and 1 on subsequent background requests.
  // We ONLY clear the map if param2 === 0, which guarantees it's an actual room load, not a background packet.
  if (param2 === 0) {
    plants.clear()
    isProcessing = false
    isInitialized = false
    currentRoomId = roomId
    console.log(`Room entry requested for room ${roomId}. Map cleared and initialization reset.`)
  }
}

const handleQuit = (): void => {
  plants.clear()
  isProcessing = false
  isInitialized = false
}

process.on('uncaughtException', (err: Error) => {
  console.error('Unhandled exception:', err)
})

extension.interceptByNameOrHash(HDirection.TOCLIENT, 'Users', handleUsers)
extension.interceptByNameOrHash(HDirection.TOSERVER, 'Chat', handleChat)
extension.interceptByNameOrHash(HDirection.TOSERVER, 'GetGuestRoom', handleGetGuestRoom)
extension.interceptByNameOrHash(HDirection.TOSERVER, 'Quit', handleQuit)
