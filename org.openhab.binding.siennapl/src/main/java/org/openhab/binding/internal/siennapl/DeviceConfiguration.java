/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.internal.siennapl;

/**
 * Configuration class for the Sienna Devices e.g.AM1, SAM1L ....
 *
 * @author s.simma - Initial contribution
 */

public class DeviceConfiguration {

    // Sienna Device Thing constants
    public static final String NEURON_ID = "neuronId";
    public static final String ENOCEAN_ID = "enoceanId";
    public static final String ENOCEAN_EEP = "enoceanEEP";
    public static final String GROUP_ID = "groupId";
    public static final String ELEMENT_ID = "elementId";
    public static final String RUNTIME = "runTime";
    public static final String SPINTIME = "spinTime";
    public static final String HW_CODE = "hwCode";
    public static final String SW_CODE = "swCode";
    public static final String ALIVE_MSG = "aliveMsg";
    public int blindActPos;
    public int angleActPos;
    public int isOnline;
    public byte answerCommand1;
    public byte answerCommand2;

    /**
     * Sienna Neuron-ID. Each Device has it's own Neuron-ID (has to be determined with the Sienna configuration
     * software)
     */
    public String neuronId;

    /**
     * Sienna Neuron-ID. Each Device has it's own Neuron-ID (has to be determined with the Sienna configuration
     * software)
     */
    public String enoceanId;

    /**
     * Enocean Gateway. Each Device has it's own Enocean-ID (has to be determined with the Sienna configuration
     * software)
     */
    public String enoceanEEP;

    /**
     * Sienna Group-ID. Has to be configured with the Sienna software (A-O). In the Binding use these values.
     */
    public String groupId;

    /**
     * Sienna Element-ID. Has to be configured with the Sienna software (1-127). In the Binding use these values.
     */
    public int elementId;

    /**
     * Sienna Runtime for Rollershutter/Blind
     */
    public int runTime;

    /**
     * Sienna Spintime for Blind
     */
    public int spinTime;

    /**
     * Sienna Hardware code of the Device
     */
    public int hwCode;

    /**
     * Sienna Software version of the Device
     */
    public int swCode;

    /**
     * Enocean device sends Alive Message
     */
    public int aliveMsg;
}
