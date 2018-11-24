/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.siennapl.internal.packet;

import static org.openhab.binding.internal.siennapl.BindingConstants.*;

import java.util.List;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.openhab.binding.internal.siennapl.DeviceConfiguration;
import org.openhab.binding.siennapl.internal.gateway.SerialConnector;
import org.openhab.binding.siennapl.internal.handler.BridgeHandler;
import org.openhab.binding.siennapl.internal.handler.DeviceHandler;
import org.openhab.binding.siennapl.internal.utils.ByteArray;
import org.openhab.binding.siennapl.internal.utils.LonCRC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handle NV Packages from/to Sienna Bridge
 *
 * @author s.simma - initial contribution
 *
 */
public class NVPacket {

    private Logger logger = LoggerFactory.getLogger(NVPacket.class);

    private BridgeHandler bridgeHandler;
    private SerialConnector connector;

    private byte command;
    private byte group;
    private byte element;
    private byte payload;
    private byte answerCmd;
    private byte answerElementOffs;

    ByteArray arbitrary = new ByteArray();
    private byte crc;

    public NVPacket(SerialConnector connector) {
        this.connector = connector;
        this.bridgeHandler = (BridgeHandler) connector.getBridge().getHandler();
    }

    // Set command
    public void setCommand(byte Command) {
        this.command = Command;
    }

    // Set Group
    public void setGroup(byte group) {
        this.group = group;
    }

    // Set Element
    public void setElement(byte element) {
        this.element = element;
    }

    // Set Payload
    public void setPayload(byte payload) {
        this.payload = payload;
    }

    // Arbitary bytes
    public void setArbitrary(ByteArray arbitrary) {
        this.arbitrary = arbitrary;
    }

    // Set expected answer command from device
    public void setAnswerCmd(byte AnswerCmd) {
        this.answerCmd = AnswerCmd;
    }

    // Set expected answer element offset from device
    public void setAnswerElementOffs(byte AnswerElementOffs) {
        this.answerElementOffs = AnswerElementOffs;
    }

    // CRC
    public void setCrc(byte Crc) {
        this.crc = Crc;
    }

    // Test CRC ok
    public boolean isValid() {
        return unsignedByte(calculateCRC()) == unsignedByte(crc);
    }

    // Send the NV package
    public void sendToBridge(SerialConnector connector) {
        connector.write(createRecord());
    }

    // Handle the received network variable (NV) command
    // To determine real command code remove bit8 first
    public void handleCommand() {
        int devCommand = command & 0x7f;
        int devGroup = group & 0x0f;
        int devElement = element & 0x7f;

        Bridge bridge = connector.getBridge();

        List<Thing> things = bridge.getThings();
        for (Thing thing : things) {
            // If it is a Sienna devices
            if (SUPPORTED_DEVICE_THING_TYPES_UIDS.contains(thing.getThingTypeUID())) {
                DeviceHandler thingHandler = (DeviceHandler) thing.getHandler();

                if ((thingHandler != null) && (thing.getHandler() instanceof DeviceHandler)) {
                    // Get device configuration for this device
                    DeviceConfiguration configuration = thingHandler.getDeviceConfiguration();

                    String thisThing = thing.getThingTypeUID().getId();

                    // Determine last valid element ID for this device
                    int devElementMin = configuration.elementId;
                    int devElementMax = devElementMin;
                    if (thisThing.equals(DEVICE_AM2X) || thisThing.equals(DEVICE_AM2L) || thisThing.equals(DEVICE_SAM2L)
                            || thisThing.equals(DEVICE_SM2)) {
                        devElementMax = devElementMax + 1;
                    } else if (thisThing.equals(DEVICE_SM4)) {
                        devElementMax = devElementMax + 3;
                    } else if (thisThing.equals(DEVICE_SM8)) {
                        devElementMax = devElementMax + 7;
                    }

                    // Test received Group/Element is for this device
                    if ((configuration.groupId.equals(Character.toString((char) (devGroup + 0x40))))
                            && ((devElement >= devElementMin) && (devElement <= devElementMax))) {
                        // ------------------ Handle AM1, SAM1L, SAM1LT --------------
                        if (thisThing.equals(DEVICE_AM1) || thisThing.equals(DEVICE_SAM1L)
                                || thisThing.equals(DEVICE_SAM1LT)) {
                            thingHandler.onDevice1OnOffChanged(devCommand, (char) (devGroup + 0x40), devElement);

                            // --------------------- Handle AM2L, AM2X, SAM2L --------------
                        } else if (thisThing.equals(DEVICE_AM2L) || thisThing.equals(DEVICE_AM2X)
                                || thisThing.equals(DEVICE_SAM2L)) {
                            thingHandler.onDeviceNchChanged(devCommand, (char) (devGroup + 0x40),
                                    configuration.elementId, (devElement - configuration.elementId));

                            // --------------------- Handle SM2, SM4, SM8 --------------
                        } else if (thisThing.equals(DEVICE_SM2) || thisThing.equals(DEVICE_SM4)
                                || thisThing.equals(DEVICE_SM8)) {
                            thingHandler.onDeviceNchChanged(devCommand, (char) (devGroup + 0x40),
                                    configuration.elementId, (devElement - configuration.elementId));

                            // --------------------- Handle SAMDR, SAMDU (Dimmer) --------------
                        } else if (thisThing.equals(DEVICE_SAMDR) || thisThing.equals(DEVICE_SAMDU)) {
                            double d = (unsignedByte(payload));
                            thingHandler.onDeviceDimmerChanged(devCommand, (char) (devGroup + 0x40), devElement, d);

                            // --------------------- Handle SAM2 (Rollershutter with inputs) --------------
                        } else if (thisThing.equals(DEVICE_SAM2)) {
                            double d = (unsignedByte(payload));
                            thingHandler.onDeviceUpDownChanged(devCommand, (char) (devGroup + 0x40), devElement, d);

                            // --------------------- Handle AM2, AMX2 (Rollershutter without inputs) --------------
                        } else if (thisThing.equals(DEVICE_AM2) || thisThing.equals(DEVICE_AMX2)) {
                            double d = (unsignedByte(payload));
                            thingHandler.onDeviceUpDownChanged(devCommand, (char) (devGroup + 0x40), devElement, d);

                            // ------------ Handle Contact from Enocean Gateway --------------
                        } else if (thisThing.equals(DEVICE_RFGS_CONTACT)) {
                            thingHandler.onDeviceEnoContactChanged(devCommand, (char) (devGroup + 0x40), devElement);

                            // ------------ Handle Motion from Enocean Gateway --------------
                        } else if (thisThing.equals(DEVICE_RFGS_MOTION)) {
                            thingHandler.onDeviceEnoMotionChanged(devCommand, (char) (devGroup + 0x40), devElement);

                            // ------------ Handle Temperature from Enocean Gateway --------------
                        } else if (thisThing.equals(DEVICE_RFGS_TEMP)) {
                            double d = (unsignedByte(payload));
                            thingHandler.onDeviceEnoTempChanged(devCommand, (char) (devGroup + 0x40), devElement, d);
                        } else {
                            logger.warn("Sienna: Device not configured or not supported: cmd:{} g:{} e:{}", devCommand,
                                    devGroup, devElement);
                        }

                        // Set This Command as last received command
                        bridgeHandler.setDeviceAnswCommand(configuration.groupId, configuration.elementId,
                                (devElement - configuration.elementId), command);

                        // Set Device is online
                        bridgeHandler.setDeviceOnline(configuration.groupId, configuration.elementId);

                        // Set Device is Online
                        thingHandler.setThingOnline();
                        break;
                    }
                } else {
                    logger.warn("Sienna: For Device {} is no ThingHandler available", thing.getThingTypeUID().getId());
                }
            } else {
                logger.warn("Sienna: Device {} is not supported by the binding", thing.getThingTypeUID().getId());
            }
        }
    }

    // Create Byte Array record (STX,CMD,Group,Element,Payload,6xDummy,CRC,ETX)
    private byte[] createRecord() {
        ByteArray data = new ByteArray();
        data.addByte(command);

        data.addByte(group);
        data.addByte(element);
        data.addByte(payload);

        // to get a total of 13 we need 6 dummy
        for (int i = 0; i < 6; i++) {
            data.addByte((byte) 0x00);
        }

        // Calculate Lon-CRC Byte
        LonCRC loncrc = new LonCRC();
        byte crc = loncrc.calcLonCRC(data.getArray());

        ByteArray record = new ByteArray();
        record.addByte(START_BYTE);
        byte[] b;
        b = data.getArray();
        record.addBytes(b);
        record.addByte(crc);
        record.addByte(STOP_BYTE);
        record.addByte(answerCmd);
        record.addByte(answerElementOffs);
        return record.getArray();
    }

    private byte calculateCRC() {
        ByteArray data = new ByteArray();
        data.addByte(command);

        data.addByte(group);
        data.addByte(element);
        data.addByte(payload);
        byte[] ba = arbitrary.getArray();
        data.addBytes(ba);

        // Calculate Lon-CRC Byte
        LonCRC loncrc = new LonCRC();
        return loncrc.calcLonCRC(data.getArray());
    }

    private int unsignedByte(byte b) {
        int unsbyte = b;
        if (unsbyte < 0) {
            unsbyte = 128 + (128 - Math.abs(unsbyte));
            return unsbyte;
        } else {
            return unsbyte;
        }
    }

}
