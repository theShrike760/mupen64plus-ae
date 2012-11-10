/**
 * Mupen64PlusAE, an N64 emulator for the Android platform
 * 
 * Copyright (C) 2012 Paul Lamb
 * 
 * This file is part of Mupen64PlusAE.
 * 
 * Mupen64PlusAE is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * Mupen64PlusAE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details. You should have received a copy of the GNU
 * General Public License along with Mupen64PlusAE. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Authors: littleguy77
 */
package paulscode.android.mupen64plusae.input;

import paulscode.android.mupen64plusae.NativeMethods;
import paulscode.android.mupen64plusae.util.Utility;

/**
 * The abstract base class for implementing all N64 controllers.
 * <p/>
 * Subclasses should implement the following pattern:
 * <ul>
 * <li>Register a listener to the upstream input (e.g. touch, keyboard, mouse, joystick, etc.).</li>
 * <li>Translate the input data into N64 controller button/axis states, and set the values of the
 * protected fields mDpad*, mBtn*, mAxis* accordingly.</li>
 * <li>Call the protected method notifyChanged().</li>
 * </ul>
 * This abstract class will call the emulator's native libraries to update game state whenever
 * notifyChanged() is called. Subclasses should not call any native methods themselves. (If they do,
 * then this abstract class should be expanded to cover those needs.)
 * <p>
 * Note that this class is stateful, in that it remembers controller button/axis state between calls
 * from the subclass. For best performance, subclasses should only call notifyChanged() when the
 * input state has actually changed, and should bundle the protected field modifications before
 * calling notifyChanged(). For example,
 * 
 * <pre>
 * {@code
 * mDpadR = true; notifyChanges(); mDpadL = false; notifyChanged(); // Inefficient
 * mDpadR = true; mDpadL = false; notifyChanged(); // Better
 * }
 * </pre>
 * 
 * @see PeripheralController
 * @see TouchscreenController
 * @see XperiaPlayController
 */
public abstract class AbstractController
{
    // Constants must match EButton listing in plugin.h! (input-sdl plug-in)
    
    /** N64 button: dpad-right. */
    public static final int DPD_R = 0;
    
    /** N64 button: dpad-left. */
    public static final int DPD_L = 1;
    
    /** N64 button: dpad-down. */
    public static final int DPD_D = 2;
    
    /** N64 button: dpad-up. */
    public static final int DPD_U = 3;
    
    /** N64 button: start. */
    public static final int START = 4;
    
    /** N64 button: trigger-z. */
    public static final int BTN_Z = 5;
    
    /** N64 button: b. */
    public static final int BTN_B = 6;
    
    /** N64 button: a. */
    public static final int BTN_A = 7;
    
    /** N64 button: cpad-right. */
    public static final int CPD_R = 8;
    
    /** N64 button: cpad-left. */
    public static final int CPD_L = 9;
    
    /** N64 button: cpad-down. */
    public static final int CPD_D = 10;
    
    /** N64 button: cpad-up. */
    public static final int CPD_U = 11;
    
    /** N64 button: shoulder-r. */
    public static final int BTN_R = 12;
    
    /** N64 button: shoulder-l. */
    public static final int BTN_L = 13;
    
    /** Total number of N64 buttons. */
    public static final int NUM_N64_BUTTONS = 14;
    
    /** The pressed state of each controller button. */
    protected boolean[] mButtonState = new boolean[NUM_N64_BUTTONS];
    
    /** The fractional value of the analog-x axis, between -1 and 1, inclusive. */
    protected float mAxisFractionX;
    
    /** The fractional value of the analog-y axis, between -1 and 1, inclusive. */
    protected float mAxisFractionY;
    
    /** The player number, between 1 and 4, inclusive. */
    private int mPlayerNumber = 1;
    
    /** The factor by which the axis fractions are scaled before going to the core. */
    private static final float AXIS_SCALE = 80;
    
    /**
     * Notifies the core that the N64 controller state has changed.
     */
    protected void notifyChanged()
    {
        int axisX = Math.round( AXIS_SCALE * Utility.clamp( mAxisFractionX, -1, 1 ) );
        int axisY = Math.round( AXIS_SCALE * Utility.clamp( mAxisFractionY, -1, 1 ) );
        NativeMethods.updateVirtualGamePadStates( mPlayerNumber - 1, mButtonState, axisX, axisY );
    }
    
    /**
     * Clears the N64 controller state, i.e. resets all button states to false and all axis
     * fractions to 0.
     */
    public void clearState()
    {
        for( int i = 0; i < NUM_N64_BUTTONS; i++ )
            mButtonState[i] = false;
        
        mAxisFractionX = 0;
        mAxisFractionY = 0;
        
        notifyChanged();
    }
    
    /**
     * Gets the player number.
     * 
     * @return The player number, between 1 and 4, inclusive.
     */
    public int getPlayerNumber()
    {
        return mPlayerNumber;
    }
    
    /**
     * Sets the player number.
     * 
     * @param player The new player number, between 1 and 4, inclusive.
     */
    public void setPlayerNumber( int player )
    {
        mPlayerNumber = player;
    }
}
