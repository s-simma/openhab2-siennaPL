/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.internal.siennapl;

import java.util.Set;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

import com.google.common.collect.ImmutableSet;

/**
 * The class defines common constants, which are
 * used across the whole binding.
 *
 * @author s.simma - Initial contribution
 */
public class BindingConstants {

    /* Fixed record lenght for communication with Sienna bridge */
    public static final int REC_LENGHT = 13;
    public static final int NEURONID_LENGHT = 12;
    public static final int BUFFER_SIZE = 4096;

    /* Start Record */
    public static final byte START_BYTE = 0x2a;
    /* End of record */
    public static final byte STOP_BYTE = 0x00;

    /* Explcit Commands from controller to gateway */
    public static final byte HELLO = 0x24;
    public static final byte FIND_DEVICES = 0x20;
    public static final byte DEVICE_HELLO = 0x2c;
    public static final byte GET_CHANNELS = 0x34;

    /* Explicit commands from gateway to controller */
    public static final byte COM_ERROR_SERIAL = 0x37;
    public static final byte COM_ERROR_BUS = 0x38;
    public static final byte DEVICE_FOUND = 0x21;
    public static final byte DEVICE_END = 0x22;
    public static final byte HELLO_EXTEND_RESPONSE = 0x2e;
    public static final byte REPORT_RFE_STATE = 0x3d;
    public static final byte REPORT_RFE_END = 0x3f;
    public static final byte SERVICE_PIN = 0x7f;

    /* NV commands from controller to gateway */
    public static final byte OFF = 0x01;
    public static final byte ON = 0x02;
    public static final byte SWITCH = 0x04;
    public static final byte SET_DIM_VALUE = 0x44;
    public static final byte SET_MOTOR_POS = 0x46;
    public static final byte SET_MOTOR_ANG = 0x48;
    public static final byte SET_RUNTIME = 0x4a;
    public static final byte GO_DOWN_DEF = 0x11;
    public static final byte GO_UP_DEF = 0x12;
    public static final byte STOP = 0x07;

    /* NV commands from gateway to controller */
    public static final byte GO = 0x04;
    public static final byte STOP_GO = 0x08;
    public static final byte STATUS_OFF = 0x09;
    public static final byte STATUS_ON = 0x0a;
    public static final byte REPORT_DIM_VALUE = 0x45;
    public static final byte GO_DOWN = 0x05;
    public static final byte GO_UP = 0x06;
    public static final byte STATUS_GO_DOWN = 0x0d;
    public static final byte STATUS_GO_UP = 0x0e;
    public static final byte STATUS_UP = 0x0c;
    public static final byte STATUS_DOWN = 0x0b;
    public static final byte REPORT_MOTOR_POS = 0x47;
    public static final byte REPORT_MOTOR_ANG = 0x49;

    // For binding internal
    public static final int DEVICE_UNDEF = 0xff;
    /* End */

    /* Each Sienna Device has an unique NeuronID/Domain/Group/Element */
    public static final String THING_NEURONID = "neuronId";
    public static final String THING_GROUPID = "groupId";
    public static final String THING_ELEMENTID = "elementId";

    public static final String BINDING_ID = "siennapl";
    public static final String BRIDGE_SIENNA = "BRIDGE";

    // List of supported device types
    public static final String DEVICE_AM1 = "AM1";
    public static final String DEVICE_AM2X = "AM2X";
    public static final String DEVICE_AM2L = "AM2L";
    public static final String DEVICE_SAM1L = "SAM1L";
    public static final String DEVICE_SAM1LT = "SAM1LT";
    public static final String DEVICE_SAM2L = "SAM2L";
    public static final String DEVICE_SM2 = "SM2";
    public static final String DEVICE_SM4 = "SM4";
    public static final String DEVICE_SM8 = "SM8";
    public static final String DEVICE_SAMDR = "SAMDR";
    public static final String DEVICE_SAMDU = "SAMDU";
    public static final String DEVICE_SAM2 = "SAM2";
    public static final String DEVICE_AM2 = "AM2";
    public static final String DEVICE_AMX2 = "AMX2";
    public static final String DEVICE_RFGS_CONTACT = "RFGS-CONTACT";
    public static final String DEVICE_RFGS_MOTION = "RFGS-MOTION";
    public static final String DEVICE_RFGS_TEMP = "RFGS-TEMP";

    // List of Channel IDs
    public static final String CHANNEL_INPUT = "input";
    public static final String CHANNEL_INPUT2 = "input2";
    public static final String CHANNEL_INPUT3 = "input3";
    public static final String CHANNEL_INPUT4 = "input4";
    public static final String CHANNEL_INPUT5 = "input5";
    public static final String CHANNEL_INPUT6 = "input6";
    public static final String CHANNEL_INPUT7 = "input7";
    public static final String CHANNEL_INPUT8 = "input8";
    public static final String CHANNEL_INPUT_UP = "input_up";
    public static final String CHANNEL_INPUT_DOWN = "input_down";
    public static final String CHANNEL_OUTLET = "outlet";
    public static final String CHANNEL_OUTLET2 = "outlet2";
    public static final String CHANNEL_OUTLET_UP = "outlet_up";
    public static final String CHANNEL_OUTLET_DOWN = "outlet_down";
    public static final String CHANNEL_INFO = "info";
    public static final String CHANNEL_POSITION = "position";
    public static final String CHANNEL_COMMAND = "command";
    public static final String CHANNEL_ANGLE = "angle";
    public static final String CHANNEL_STATE = "state";
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_VALUE = "value";

    // List of all Thing Type UIDs
    public static final ThingTypeUID BRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, BRIDGE_SIENNA);
    public static final ThingTypeUID AM1_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_AM1);
    public static final ThingTypeUID AM2X_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_AM2X);
    public static final ThingTypeUID AM2L_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_AM2L);
    public static final ThingTypeUID SAM1L_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SAM1L);
    public static final ThingTypeUID SAM1LT_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SAM1L);
    public static final ThingTypeUID SAM2L_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SAM2L);
    public static final ThingTypeUID SM2_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SM2);
    public static final ThingTypeUID SM4_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SM4);
    public static final ThingTypeUID SM8_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SM8);
    public static final ThingTypeUID SAMDR_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SAMDR);
    public static final ThingTypeUID SAMDU_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SAMDU);
    public static final ThingTypeUID SAM2_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_SAM2);
    public static final ThingTypeUID AM2_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_AM2);
    public static final ThingTypeUID AMX2_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_AMX2);
    public static final ThingTypeUID RFGS_CONTACT_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_RFGS_CONTACT);
    public static final ThingTypeUID RFGS_MOTION_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_RFGS_MOTION);
    public static final ThingTypeUID RFGS_TEMP_THING_TYPE = new ThingTypeUID(BINDING_ID, DEVICE_RFGS_TEMP);

    public static final Set<ThingTypeUID> SUPPORTED_BRIDGE_THING_TYPES_UIDS = ImmutableSet.of(BRIDGE_THING_TYPE);

    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = ImmutableSet.of(BRIDGE_THING_TYPE,
            AM1_THING_TYPE, AM2X_THING_TYPE, AM2L_THING_TYPE, SAM1L_THING_TYPE, SAM1LT_THING_TYPE, SAM2L_THING_TYPE,
            SM2_THING_TYPE, SM4_THING_TYPE, SM8_THING_TYPE, SAMDR_THING_TYPE, SAMDU_THING_TYPE, SAM2_THING_TYPE,
            AM2_THING_TYPE, AMX2_THING_TYPE, RFGS_CONTACT_THING_TYPE, RFGS_MOTION_THING_TYPE, RFGS_TEMP_THING_TYPE);

    public static final Set<ThingTypeUID> SUPPORTED_DEVICE_THING_TYPES_UIDS = ImmutableSet.of(AM1_THING_TYPE,
            AM2X_THING_TYPE, AM2L_THING_TYPE, SAM1L_THING_TYPE, SAM1LT_THING_TYPE, SAM2L_THING_TYPE, SM2_THING_TYPE,
            SM4_THING_TYPE, SM8_THING_TYPE, SAMDR_THING_TYPE, SAMDU_THING_TYPE, SAM2_THING_TYPE, AM2_THING_TYPE,
            AMX2_THING_TYPE, RFGS_CONTACT_THING_TYPE, RFGS_MOTION_THING_TYPE, RFGS_TEMP_THING_TYPE);

}
