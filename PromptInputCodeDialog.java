package paulscode.android.mupen64plusae.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.bda.controller.Controller;

import org.mupen64plusae.v3.alpha.R;

import java.util.ArrayList;
import java.util.List;

import paulscode.android.mupen64plusae.input.provider.AbstractProvider;
import paulscode.android.mupen64plusae.input.provider.AbstractProvider.OnInputListener;
import paulscode.android.mupen64plusae.input.provider.AxisProvider;
import paulscode.android.mupen64plusae.input.provider.KeyProvider;
import paulscode.android.mupen64plusae.input.provider.KeyProvider.ImeFormula;
import paulscode.android.mupen64plusae.input.provider.MogaProvider;

public class PromptInputCodeDialog extends DialogFragment
{
    private static final String STATE_TITLE = "STATE_TITLE";
    private static final String STATE_MESSAGE = "STATE_MESSAGE";
    private static final String STATE_NEUTRAL_BUTTON_TEXT = "STATE_NEUTRAL_BUTTON_TEXT";
    private static final String STATE_NUM_ITEMS = "STATE_NUM_ITEMS";
    private static final String STATE_ITEMS = "STATE_ITEMS";

    /**
     * The listener interface for receiving an input code provided by the user.
     * 
     * @see PromptInputCodeDialog
     */
    public interface PromptInputCodeListener
    {
        /**
         * Called when the dialog is dismissed and should be used to process the
         * input code provided by the user.
         * 
         * @param inputCode
         *            The input code provided by the user, or 0 if the user
         *            clicks one of the dialog's buttons.
         * @param hardwareId
         *            The identifier of the source device, or 0 if the user
         *            clicks one of the dialog's buttons.
         * @param which
         *            The DialogInterface button pressed by the user.
         */
        public void onDialogClosed(int inputCode, int hardwareId, int which);
        
        /**
         * Returns an instance of the Moga controller
         * @return instance of the Moga controller
         */
        public Controller getMogaController();
    }

    public static PromptInputCodeDialog newInstance(String title, String message, String neutralButtonText, List<Integer> ignoredKeyCodes)
    {
        PromptInputCodeDialog frag = new PromptInputCodeDialog();
        Bundle args = new Bundle();
        args.putString(STATE_TITLE, title);
        args.putString(STATE_MESSAGE, message);
        args.putString(STATE_NEUTRAL_BUTTON_TEXT, neutralButtonText);

        args.putInt(STATE_NUM_ITEMS, ignoredKeyCodes.size());

        for (int index = 0; index < ignoredKeyCodes.size(); ++index)
        {
            Integer seq = ignoredKeyCodes.get(index);
            args.putInt(STATE_ITEMS + index, seq);
        }

        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState)
    {
        setRetainInstance(true);

        final String title = getArguments().getString(STATE_TITLE);
        final String message = getArguments().getString(STATE_MESSAGE);
        final String neutralButtonText = getArguments().getString(STATE_NEUTRAL_BUTTON_TEXT);
        final int numItems = getArguments().getInt(STATE_NUM_ITEMS);

        List<Integer> ignoredKeyCodes = new ArrayList<Integer>();

        for (int index = 0; index < numItems; ++index)
        {
            Integer seq = getArguments().getInt(STATE_ITEMS + index);
            ignoredKeyCodes.add(seq);
        }

        final ArrayList<AbstractProvider> providers = new ArrayList<AbstractProvider>();

        // Create a widget to dispatch key/motion event data
        FrameLayout view = new FrameLayout(getActivity());
        ImageView image = new ImageView(getActivity());
        image.setImageResource(R.drawable.ic_controller);
        EditText dummyImeListener = new EditText(getActivity());
        dummyImeListener.setVisibility(View.INVISIBLE);
        dummyImeListener.setHeight(0);
        view.addView(image);
        view.addView(dummyImeListener);

        // Set the focus parameters of the view so that it will dispatch events
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.requestFocus();

        // Create the input event providers
        providers.add(new KeyProvider(view, ImeFormula.DEFAULT, ignoredKeyCodes));
        
        if (getActivity() instanceof PromptInputCodeListener)
        {
            providers.add(new MogaProvider(((PromptInputCodeListener) getActivity()).getMogaController()));
        }
        else
        {
            Log.e("PromptInputCodeDialog", "Activity doesn't implement PromptInputCodeListener");
        }

        // Register a new axis provider to listen for joystick inputs
        providers.add(new AxisProvider(view));

        // Notify the client when the user clicks the dialog's positive button
        DialogInterface.OnClickListener clickListener = new OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                onInputCommon(providers, getActivity(), 0, 0, which);
            }
        };

        // Create the user input dialog window
        Builder builder = new Builder(getActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setNegativeButton(getActivity().getString(android.R.string.cancel), clickListener);
        builder.setNeutralButton(neutralButtonText, clickListener);
        builder.setPositiveButton(null, null);
        builder.setView(view);

        // Create our dialog (note: this does not draw/show it, that is done by the user of
        // this class)
        final AlertDialog promptInputCodeDialog = builder.create();

        OnInputListener inputListener = new OnInputListener()
        {
            private float[] mStrengths = null;

            // This overriden version of onInput is tracking joystick or touchpad changes as they will appears as an array of values
            // instead of a single input (i.e. button click). Also note that its expected that the first time onInput is called it will
            // pass through and simply assign the array of strengths to mStrengths. I think the intention is that we want a baseline reading
            // of the inputs with the idea that when a user actually moves a stick on the controller that the value will be significantly different
            // and thus we will find a real motion and not a accidental/steady state value (which is when no one is touching the sticks)
            @Override
            public void onInput(int[] inputCodes, float[] strengths, int hardwareId)
            {
                // I guess this is checking for a bogus onInput event
                if (inputCodes == null || strengths == null)
                {
                    return;
                }

                //The size of the strength array changed. We need to reset. why/how could this ever happen?
                if(mStrengths != null && (mStrengths.length != inputCodes.length))
                {
                    mStrengths = null;
                }

                // Stores the strongest input value found
                float strongestInputValue = -1;
                // Stores the strongest input code
                int strongestInputCode = 0;

                Log.e("PromptInputCodeDialog", "Input check start");

                // Loop through the array of values that was passed in
                for (int i = 0; i < inputCodes.length; i++)
                {
                    if(strengths[i] != 0.0f)
                    {
                        Log.e("PromptInputCodeDialog", "Input[" + i + "] = " + strengths[i] + " strongestInputValue = " + strongestInputValue);
                    }

                    // First check for an input greater than 0, then check to ensure we have an initialized mStrengths array, then
                    // compare the two values, looking for a significant difference (i.e. > 0.1).
                    if ((Math.abs(strengths[i]) > 0) && (mStrengths != null) && !(compareStrengths(mStrengths[i], strengths[i], 0.1f)))
                    {
                        Log.e("PromptInputCodeDialog", "Detected change @input[" + i + "] with strength = " + strengths[i]);

                        // Save off the newly found biggest strength value and the associated input code
                        strongestInputValue = strengths[i];
                        strongestInputCode = inputCodes[i];
                    }
                }

                Log.e("PromptInputCodeDialog", "Input check end");

                if ((strongestInputValue != -1) && (strongestInputCode != 0))
                {
                    // Call the overriden version below as we have narrowed down the possible
                    // inputs to a single change
                    onInput(strongestInputCode, strongestInputValue, hardwareId);
                }

                // Save off the last list of strength values
                mStrengths = strengths;
            }

            // This overriden version is for single button click events and when a specific(i.e. one axis) joystick motion
            // has been detected
            @Override
            public void onInput(int inputCode, float strength, int hardwareId)
            {
                if (inputCode != 0)
                {
                    onInputCommon(providers, getActivity(), inputCode, hardwareId, DialogInterface.BUTTON_POSITIVE);

                    promptInputCodeDialog.dismiss();
                }
            }
        };

        // Connect the upstream event listeners
        for (AbstractProvider provider : providers)
        {
            provider.registerListener(inputListener);
        }

        return promptInputCodeDialog;
    }

    /**
     * Returns true if the two strength values are about the same
     * @param strengths1
     * @param strengths2
     * @return true if the two values are about the same
     */
    private boolean compareStrengths(float strengths1, float strengths2, float delta)
    {
        boolean areTheyAboutSame = false;

        if (Math.abs(strengths1 - strengths2) < delta)
        {
            areTheyAboutSame = true;
        }

        return areTheyAboutSame;
    }

    @Override
    public void onDestroyView()
    {
        // This is needed because of this:
        // https://code.google.com/p/android/issues/detail?id=17423

        if (getDialog() != null && getRetainInstance())
            getDialog().setDismissMessage(null);
        super.onDestroyView();
    }

    /**
     * @param providers
     * @param activity
     * @param inputCode
     * @param hardwareId
     * @param which
     */
    void onInputCommon(final ArrayList<AbstractProvider> providers, Activity activity, int inputCode, int hardwareId, int which)
    {
        // Why are we unregistering the listeners?
        for (AbstractProvider provider : providers)
        {
            provider.unregisterAllListeners();
        }

        if (activity instanceof PromptInputCodeListener)
        {
            ((PromptInputCodeListener) activity).onDialogClosed(inputCode, hardwareId, which);
        }
        else
        {
            Log.e("PromptInputCodeDialog", "Activity doesn't implement PromptInputCodeListener");
        }
    }
}