package com.voiddrop.app.properties

import android.net.Uri
import com.voiddrop.app.domain.model.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import io.mockk.every
import io.mockk.mockk

/**
 * Property-based tests for data models.
 * **Feature: voiddrop-native-mvp, Property 18: Android Platform Integration**
 * **Validates: Requirements 8.1, 8.2, 8.3, 8.4**
 */
class DataModelProperties : StringSpec({

    "PairingCode should always have valid structure and non-empty fields" {
        checkAll(
            Arb.string(1..50),
            Arb.string(1..100),
            Arb.string(1..100),
            Arb.string(1..500),
            Arb.list(Arb.string(10..100), 1..5),
            Arb.long(System.currentTimeMillis()..System.currentTimeMillis() + 86400000), // Next 24 hours
            Arb.string(10..1000)
        ) { id, deviceId, deviceName, signalingData, iceServers, expirationTime, qrCodeData ->
            
            val connectionInfo = ConnectionInfo(
                deviceId = deviceId,
                deviceName = deviceName,
                signalingData = signalingData,
                iceServers = iceServers
            )
            
            val pairingCode = PairingCode(
                id = id,
                connectionInfo = connectionInfo,
                expirationTime = expirationTime,
                qrCodeData = qrCodeData
            )

            // Properties that should always hold
            pairingCode.id shouldBe id
            pairingCode.id.shouldNotBeEmpty()
            pairingCode.connectionInfo shouldBe connectionInfo
            pairingCode.expirationTime shouldBe expirationTime
            pairingCode.qrCodeData shouldBe qrCodeData
            pairingCode.qrCodeData.shouldNotBeEmpty()
        }
    }

    "ConnectionInfo should maintain data integrity across all valid inputs" {
        checkAll(
            Arb.string(1..100),
            Arb.string(1..100),
            Arb.string(1..1000),
            Arb.list(Arb.string(10..100), 0..10)
        ) { deviceId, deviceName, signalingData, iceServers ->
            
            val connectionInfo = ConnectionInfo(
                deviceId = deviceId,
                deviceName = deviceName,
                signalingData = signalingData,
                iceServers = iceServers
            )

            // Properties that should always hold
            connectionInfo.deviceId shouldBe deviceId
            connectionInfo.deviceId.shouldNotBeEmpty()
            connectionInfo.deviceName shouldBe deviceName
            connectionInfo.deviceName.shouldNotBeEmpty()
            connectionInfo.signalingData shouldBe signalingData
            connectionInfo.signalingData.shouldNotBeEmpty()
            connectionInfo.iceServers shouldBe iceServers
        }
    }

    "TransferProgress should always calculate valid progress percentage" {
        checkAll(
            Arb.string(1..50),
            Arb.string(1..100),
            Arb.long(0..Long.MAX_VALUE / 2), // Avoid overflow
            Arb.long(0..Long.MAX_VALUE / 2),
            Arb.enum<TransferStatus>(),
            Arb.string(0..200).orNull()
        ) { transferId, fileName, totalBytes, transferredBytes, status, error ->
            
            // Ensure transferred bytes don't exceed total bytes for realistic scenarios
            val actualTransferredBytes = if (totalBytes > 0) {
                minOf(transferredBytes, totalBytes)
            } else {
                0L
            }
            
            val transferProgress = TransferProgress(
                transferId = transferId,
                fileName = fileName,
                totalBytes = totalBytes,
                transferredBytes = actualTransferredBytes,
                status = status,
                error = error
            )

            // Properties that should always hold
            transferProgress.transferId shouldBe transferId
            transferProgress.transferId.shouldNotBeEmpty()
            transferProgress.fileName shouldBe fileName
            transferProgress.fileName.shouldNotBeEmpty()
            transferProgress.totalBytes shouldBe totalBytes
            transferProgress.transferredBytes shouldBe actualTransferredBytes
            transferProgress.status shouldBe status
            transferProgress.error shouldBe error
            
            // Progress percentage should always be between 0 and 100
            transferProgress.progressPercentage shouldBe (
                if (totalBytes > 0) {
                    (actualTransferredBytes.toFloat() / totalBytes.toFloat()) * 100f
                } else {
                    0f
                }
            )
            
            // Progress should be in valid range
            assert(transferProgress.progressPercentage >= 0f)
            assert(transferProgress.progressPercentage <= 100f)
        }
    }

    "FileInfo should format file sizes correctly for all valid inputs" {
        checkAll(
            Arb.string(1..255), // Valid filename length
            Arb.long(0..Long.MAX_VALUE / 1024), // Avoid overflow in size calculations
            Arb.string(1..100), // MIME type
            Arb.long().orNull()
        ) { fileName, size, mimeType, transferDate ->
            
            val mockUri = mockk<Uri>()
            every { mockUri.toString() } returns "content://test/$fileName"
            
            val fileInfo = FileInfo(
                uri = mockUri,
                name = fileName,
                size = size,
                mimeType = mimeType,
                transferDate = transferDate
            )

            // Properties that should always hold
            fileInfo.name shouldBe fileName
            fileInfo.name.shouldNotBeEmpty()
            fileInfo.size shouldBe size
            fileInfo.mimeType shouldBe mimeType
            fileInfo.mimeType.shouldNotBeEmpty()
            fileInfo.transferDate shouldBe transferDate
            
            // Formatted size should always be valid
            val formattedSize = fileInfo.getFormattedSize()
            formattedSize.shouldNotBeEmpty()
            
            // Should contain a number and a unit
            val parts = formattedSize.split(" ")
            assert(parts.size == 2) { "Formatted size should have number and unit: $formattedSize" }
            
            val number = parts[0].toFloatOrNull()
            assert(number != null && number >= 0) { "Size number should be valid and non-negative: ${parts[0]}" }
            
            val unit = parts[1]
            assert(unit in listOf("B", "KB", "MB", "GB", "TB")) { "Unit should be valid: $unit" }
        }
    }

    "TransferRecord should maintain referential integrity" {
        checkAll(
            Arb.string(1..50),
            Arb.list(
                Arb.bind(
                    Arb.string(1..255),
                    Arb.long(0..Long.MAX_VALUE / 1024),
                    Arb.string(1..100)
                ) { name, size, mimeType ->
                    val mockUri = mockk<Uri>()
                    every { mockUri.toString() } returns "content://test/$name"
                    FileInfo(mockUri, name, size, mimeType)
                },
                0..10
            ),
            Arb.string(1..50),
            Arb.enum<TransferDirection>(),
            Arb.long(0..System.currentTimeMillis()),
            Arb.enum<TransferStatus>()
        ) { id, files, peerId, direction, timestamp, status ->
            
            val transferRecord = TransferRecord(
                id = id,
                files = files,
                peerId = peerId,
                direction = direction,
                timestamp = timestamp,
                status = status
            )

            // Properties that should always hold
            transferRecord.id shouldBe id
            transferRecord.id.shouldNotBeEmpty()
            transferRecord.files shouldBe files
            transferRecord.peerId shouldBe peerId
            transferRecord.peerId.shouldNotBeEmpty()
            transferRecord.direction shouldBe direction
            transferRecord.timestamp shouldBe timestamp
            transferRecord.status shouldBe status
            
            // Timestamp should be reasonable
            assert(timestamp >= 0) { "Timestamp should be non-negative" }
        }
    }

    "PeerInfo should track connection state consistently" {
        checkAll(
            Arb.string(1..50),
            Arb.string(1..100),
            Arb.enum<ConnectionState>(),
            Arb.long(0..System.currentTimeMillis())
        ) { peerId, deviceName, connectionState, lastSeen ->
            
            val peerInfo = PeerInfo(
                peerId = peerId,
                deviceName = deviceName,
                connectionState = connectionState,
                lastSeen = lastSeen
            )

            // Properties that should always hold
            peerInfo.peerId shouldBe peerId
            peerInfo.peerId.shouldNotBeEmpty()
            peerInfo.deviceName shouldBe deviceName
            peerInfo.deviceName.shouldNotBeEmpty()
            peerInfo.connectionState shouldBe connectionState
            peerInfo.lastSeen shouldBe lastSeen
            
            // Last seen should be reasonable
            assert(lastSeen >= 0) { "Last seen timestamp should be non-negative" }
        }
    }

    "SignalingMessage should preserve WebRTC signaling data integrity" {
        checkAll(
            Arb.enum<SignalingType>(),
            Arb.string(1..10000), // WebRTC payloads can be large
            Arb.string(1..50),
            Arb.string(1..50)
        ) { type, payload, fromPeer, toPeer ->
            
            val signalingMessage = SignalingMessage(
                type = type,
                payload = payload,
                fromPeer = fromPeer,
                toPeer = toPeer
            )

            // Properties that should always hold
            signalingMessage.type shouldBe type
            signalingMessage.payload shouldBe payload
            signalingMessage.payload.shouldNotBeEmpty()
            signalingMessage.fromPeer shouldBe fromPeer
            signalingMessage.fromPeer.shouldNotBeEmpty()
            signalingMessage.toPeer shouldBe toPeer
            signalingMessage.toPeer.shouldNotBeEmpty()
            
            // Peers should be different for meaningful communication
            if (fromPeer != toPeer) {
                signalingMessage.fromPeer shouldNotBe signalingMessage.toPeer
            }
        }
    }

    "DataMessage should handle byte array data correctly" {
        checkAll(
            Arb.enum<MessageType>(),
            Arb.byteArray(Arb.int(0..10000), Arb.byte()), // Variable size byte arrays
            Arb.string(1..50),
            Arb.map(Arb.string(1..20), Arb.string(1..100), minSize = 0, maxSize = 10)
        ) { type: MessageType, payload: ByteArray, fromPeer: String, metadata: Map<String, String> ->
            
            val dataMessage = DataMessage(
                type = type,
                payload = payload,
                fromPeer = fromPeer,
                metadata = metadata
            )

            // Properties that should always hold
            dataMessage.type shouldBe type
            dataMessage.payload shouldBe payload
            dataMessage.fromPeer shouldBe fromPeer
            dataMessage.fromPeer.shouldNotBeEmpty()
            dataMessage.metadata shouldBe metadata
            
            // Test equality and hash code consistency
            val identicalMessage = DataMessage(
                type = type,
                payload = payload.copyOf(), // Create identical byte array
                fromPeer = fromPeer,
                metadata = metadata
            )
            
            dataMessage shouldBe identicalMessage
            dataMessage.hashCode() shouldBe identicalMessage.hashCode()
        }
    }

    "All enum values should be accessible and consistent" {
        checkAll(Arb.constant(Unit)) { _ ->
            
            // TransferStatus should have all required values
            val transferStatuses = TransferStatus.values()
            assert(transferStatuses.contains(TransferStatus.PENDING))
            assert(transferStatuses.contains(TransferStatus.IN_PROGRESS))
            assert(transferStatuses.contains(TransferStatus.COMPLETED))
            assert(transferStatuses.contains(TransferStatus.FAILED))
            assert(transferStatuses.contains(TransferStatus.CANCELLED))
            
            // ConnectionState should have all required values
            val connectionStates = ConnectionState.values()
            assert(connectionStates.contains(ConnectionState.CONNECTING))
            assert(connectionStates.contains(ConnectionState.CONNECTED))
            assert(connectionStates.contains(ConnectionState.DISCONNECTED))
            assert(connectionStates.contains(ConnectionState.FAILED))
            
            // TransferDirection should have all required values
            val transferDirections = TransferDirection.values()
            assert(transferDirections.contains(TransferDirection.SENT))
            assert(transferDirections.contains(TransferDirection.RECEIVED))
            
            // SignalingType should have all required values
            val signalingTypes = SignalingType.values()
            assert(signalingTypes.contains(SignalingType.OFFER))
            assert(signalingTypes.contains(SignalingType.ANSWER))
            assert(signalingTypes.contains(SignalingType.ICE_CANDIDATE))
            assert(signalingTypes.contains(SignalingType.PAIRING_REQUEST))
            
            // MessageType should have all required values
            val messageTypes = MessageType.values()
            assert(messageTypes.contains(MessageType.FILE_HEADER))
            assert(messageTypes.contains(MessageType.FILE_CHUNK))
            assert(messageTypes.contains(MessageType.FILE_COMPLETE))
            assert(messageTypes.contains(MessageType.TRANSFER_ACK))
        }
    }
})