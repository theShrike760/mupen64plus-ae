/**
 * Mupen64PlusAE, an N64 emulator for the Android platform
 * 
 * Copyright (C) 2013 Paul Lamb
 * 
 * This file is part of Mupen64PlusAE.
 * 
 * Mupen64PlusAE is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * Mupen64PlusAE is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with Mupen64PlusAE. If
 * not, see <http://www.gnu.org/licenses/>.
 * 
 * Authors: littleguy77
 */
package paulscode.android.mupen64plusae.profile;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.v7.preference.EditTextPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceCategory;
import android.support.v7.preference.PreferenceGroup;

import org.mupen64plusae.v3.fzurita.R;

import java.util.ArrayList;
import java.util.Arrays;

import paulscode.android.mupen64plusae.persistent.AppData;
import paulscode.android.mupen64plusae.persistent.GlobalPrefs;
import paulscode.android.mupen64plusae.preference.CompatListPreference;
import paulscode.android.mupen64plusae.preference.PrefUtil;

public class EmulationProfileActivity extends ProfileActivity
{
    // These constants must match the keys found in preferences_emulation.xml
    private static final String SCREEN_ROOT = "screenRoot";
    private static final String CATEGORY_RICE = "categoryRice";
    private static final String CATEGORY_GLN64 = "categoryGln64";
    private static final String CATEGORY_GLIDE64 = "categoryGlide64";
    private static final String CATEGORY_GLIDE64_ADVANCED = "categoryGlide64Advanced";
    private static final String CATEGORY_GLIDEN64_TEXTURE = "categoryGliden64Texture";
    private static final String CATEGORY_GLIDEN64_GENERAL = "categoryGliden64General";
    private static final String CATEGORY_GLIDEN64_FRAME_BUFFER = "categoryGliden64FrameBuffer";
    private static final String CATEGORY_GLIDEN64_TEXTURE_FILTERING = "categoryGliden64TextureFiltering";
    private static final String CATEGORY_GLIDEN64_BLOOM = "categoryGliden64Bloom";
    private static final String CATEGORY_GLIDEN64_GAMMA = "categoryGliden64Gamma";
    private static final String CATEGORY_ANGRYLION = "categoryAngrylion";

    private static final String RSP_PLUGIN = "rspSetting";
    private static final String VIDEO_PLUGIN = "videoPlugin";

    // These constants must match the entry-values found in arrays.xml
    private static final String LIBGLIDE64_SO = "libmupen64plus-video-glide64mk2.so";
    private static final String LIBGLIDEN64_SO = "libmupen64plus-video-gliden64.so";
    private static final String LIBRICE_SO = "libmupen64plus-video-rice.so";
    private static final String LIBGLN64_SO = "libmupen64plus-video-gln64.so";
    private static final String LIBANGRYLION_SO = "libmupen64plus-video-angrylion.so";

    // Preference menu items
    private PreferenceGroup mScreenRoot = null;
    private PreferenceCategory mCategoryN64 = null;
    private PreferenceCategory mCategoryRice = null;
    private PreferenceCategory mCategoryGlide64 = null;
    private PreferenceCategory mCategoryGlide64Advanced = null;
    private PreferenceCategory mCategoryGliden64Texture = null;
    private PreferenceCategory mCategoryGliden64General = null;
    private PreferenceCategory mCategoryGliden64FrameBuffer = null;
    private PreferenceCategory mCategoryGliden64TextureFiltering = null;
    private PreferenceCategory mCategoryGliden64Bloom = null;
    private PreferenceCategory mCategoryGliden64Gamma = null;
    private PreferenceCategory mCategoryAngrylion = null;

    private CompatListPreference mPreferenceRspPlugin = null;
    private CompatListPreference mPreferenceVideoPlugin = null;

    private String mCurrentVideoPlugin = null;
    
    @Override
    protected int getPrefsResId()
    {
        return R.xml.profile_emulation;
    }
    
    @Override
    protected String getConfigFilePath()
    {
        AppData appData = new AppData( this );
        return new GlobalPrefs( this, appData ).emulationProfiles_cfg;
    }
    
    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );
        
        // Ensure that selected plugin names and other list preferences are valid
        Resources res = getResources();
        PrefUtil.validateListPreference( res, mPrefs, VIDEO_PLUGIN, R.string.videoPlugin_default,
                R.array.videoPlugin_values );
    }
    
    @Override
    public void onSharedPreferenceChanged( SharedPreferences sharedPreferences, String key )
    {
        if(key.equals("videoPlugin"))
        {
            resetPreferences();
        }

        Preference pref = findPreference(key);
        
        if (pref instanceof EditTextPreference)
        {
            EditTextPreference editTextPref = (EditTextPreference) pref;
            pref.setSummary(editTextPref.getText());
        }

        super.onSharedPreferenceChanged( sharedPreferences, key );
    }

    @Override
    protected void refreshViews()
    {        
        // Get some menu items for use later
        mScreenRoot = (PreferenceGroup) findPreference( SCREEN_ROOT );
        mCategoryN64 = (PreferenceCategory) findPreference( CATEGORY_GLN64 );
        mCategoryRice = (PreferenceCategory) findPreference( CATEGORY_RICE );
        mCategoryGlide64 = (PreferenceCategory) findPreference( CATEGORY_GLIDE64 );
        mCategoryGlide64Advanced = (PreferenceCategory) findPreference( CATEGORY_GLIDE64_ADVANCED);
        mCategoryGliden64Texture = (PreferenceCategory) findPreference( CATEGORY_GLIDEN64_TEXTURE );
        mCategoryGliden64General = (PreferenceCategory) findPreference( CATEGORY_GLIDEN64_GENERAL );
        mCategoryGliden64FrameBuffer = (PreferenceCategory) findPreference( CATEGORY_GLIDEN64_FRAME_BUFFER );
        mCategoryGliden64TextureFiltering = (PreferenceCategory) findPreference( CATEGORY_GLIDEN64_TEXTURE_FILTERING );
        mCategoryGliden64Bloom = (PreferenceCategory) findPreference( CATEGORY_GLIDEN64_BLOOM );
        mCategoryGliden64Gamma = (PreferenceCategory) findPreference( CATEGORY_GLIDEN64_GAMMA );
        mCategoryAngrylion = (PreferenceCategory) findPreference( CATEGORY_ANGRYLION );

        mPreferenceRspPlugin = (CompatListPreference) findPreference( RSP_PLUGIN );
        mPreferenceVideoPlugin = (CompatListPreference) findPreference( VIDEO_PLUGIN );

        // Get the current values
        String videoPlugin = mPrefs.getString( VIDEO_PLUGIN, null );
        
        String openGlVersion = AppData.getOpenGlEsVersion(this);

        //Remove or add options depending on GLES version
        if(openGlVersion.equals("2.0"))
        {
            if(mPreferenceVideoPlugin != null) {
                //Don't allow angrylion
                ArrayList<CharSequence> videoEntriesArray = new ArrayList<CharSequence>(Arrays.asList(mPreferenceVideoPlugin.getEntries()));
                ArrayList<CharSequence> videoValuesArray = new ArrayList<CharSequence>(Arrays.asList(mPreferenceVideoPlugin.getEntryValues()));

                int angryLionIndex = videoEntriesArray.indexOf(getText(R.string.videoPlugin_entryAngrylion));
                if(angryLionIndex != -1)
                {
                    videoEntriesArray.remove(angryLionIndex);
                    videoValuesArray.remove(videoValuesArray.indexOf("libmupen64plus-video-angrylion.so"));
                }

                mPreferenceVideoPlugin.setEntries(videoEntriesArray.toArray(new CharSequence[videoEntriesArray.size()]));
                mPreferenceVideoPlugin.setEntryValues(videoValuesArray.toArray(new CharSequence[videoValuesArray.size()]));
            }
        }
        
        // Hide certain categories altogether if they're not applicable. Normally we just rely on
        // the built-in dependency disabler, but here the categories are so large that hiding them
        // provides a better user experience.
        
        if(mCategoryN64 != null)
        {
            if( LIBGLN64_SO.equals( videoPlugin ) )
            {
                mScreenRoot.addPreference( mCategoryN64 );
            }
            else
            {
                mScreenRoot.removePreference( mCategoryN64 );
            }
        }

        if(mCategoryRice != null)
        {
            if( LIBRICE_SO.equals( videoPlugin ) )
            {
                mScreenRoot.addPreference( mCategoryRice );
            }
            else
            {
                mScreenRoot.removePreference( mCategoryRice );
            }

        }

        if(mCategoryGlide64 != null &&
            mCategoryGlide64 != null)
        {
            if( LIBGLIDE64_SO.equals( videoPlugin ) )
            {
                mScreenRoot.addPreference( mCategoryGlide64 );
                mScreenRoot.addPreference( mCategoryGlide64Advanced );
            }
            else
            {
                mScreenRoot.removePreference( mCategoryGlide64 );
                mScreenRoot.removePreference( mCategoryGlide64Advanced );
            }
        }

        if(mCategoryAngrylion != null)
        {
            if( LIBANGRYLION_SO.equals( videoPlugin ) )
            {
                mScreenRoot.addPreference( mCategoryAngrylion );
            }
            else
            {
                mScreenRoot.removePreference( mCategoryAngrylion );
            }
        }

        if(mCategoryGliden64Texture != null &&
            mCategoryGliden64General != null &&
            mCategoryGliden64FrameBuffer != null &&
            mCategoryGliden64TextureFiltering != null &&
            mCategoryGliden64Bloom != null &&
            mCategoryGliden64Gamma != null)
        {
            if( LIBGLIDEN64_SO.equals( videoPlugin ) )
            {
                mScreenRoot.addPreference( mCategoryGliden64Texture );
                mScreenRoot.addPreference( mCategoryGliden64General );
                mScreenRoot.addPreference( mCategoryGliden64FrameBuffer );
                mScreenRoot.addPreference( mCategoryGliden64TextureFiltering );
                mScreenRoot.addPreference( mCategoryGliden64Bloom );
                mScreenRoot.addPreference( mCategoryGliden64Gamma );
            }
            else
            {
                mScreenRoot.removePreference( mCategoryGliden64Texture );
                mScreenRoot.removePreference( mCategoryGliden64General );
                mScreenRoot.removePreference( mCategoryGliden64FrameBuffer );
                mScreenRoot.removePreference( mCategoryGliden64TextureFiltering );
                mScreenRoot.removePreference( mCategoryGliden64Bloom );
                mScreenRoot.removePreference( mCategoryGliden64Gamma );
            }
        }

        //Limit RSP options based on plugin
        if(mPreferenceRspPlugin != null)
        {
            ArrayList<String> entries = new ArrayList<String>();
            ArrayList<String> values = new ArrayList<String>();

            if( LIBGLN64_SO.equals( videoPlugin ) || LIBRICE_SO.equals( videoPlugin ) || LIBGLIDE64_SO.equals( videoPlugin ))
            {
                //Don't allow LLE mode
                entries.add(getString(R.string.rsp_hle));
                entries.add(getString(R.string.rsp_cxd4_hle));
                values.add("rsp-hle");
                values.add("rsp-cxd4-hle");
            }
            else if(LIBANGRYLION_SO.equals( videoPlugin ))
            {
                //Don't allow HLE
                entries.add(getString(R.string.rsp_cxd4_lle));
                values.add("rsp-cxd4-lle");
            }
            else
            {
                //All options available
                entries.add(getString(R.string.rsp_hle));
                entries.add(getString(R.string.rsp_cxd4_hle));
                entries.add(getString(R.string.rsp_cxd4_lle));
                values.add("rsp-hle");
                values.add("rsp-cxd4-hle");
                values.add("rsp-cxd4-lle");
            }

            String[] entriesArray = entries.toArray(new String[entries.size()]);
            String[] valuesArray = values.toArray(new String[values.size()]);

            mPreferenceRspPlugin.setEntries(entriesArray);
            mPreferenceRspPlugin.setEntryValues(valuesArray);

            //Only update the selected option if the plugin changed
            if(mCurrentVideoPlugin != null && !mCurrentVideoPlugin.equals(videoPlugin))
            {
                if(mPreferenceRspPlugin != null && mPreferenceRspPlugin.getEntryValues().length != 0)
                {
                    mPreferenceRspPlugin.setValue(mPreferenceRspPlugin.getEntryValues()[0].toString());
                }

                mPrefs.edit().apply();
            }
        }

        if(videoPlugin != null)
        {
            mCurrentVideoPlugin = videoPlugin;
        }
    }

    /**
     * Check for override for a specific key
     * @param key Key to check for override value
     * @param currentValue The current value for the key
     * @return The overriden value for the key
     */
    @Override
    protected String checkForOverride(final String key, final String currentValue)
    {
        String value = currentValue;
        if(value != null)
        {
            //Support older string value for video plugin that could support multiple GLideN64 versions
            //There is now only one version
            if(key.equals("videoPlugin") &&
                    value.contains("libmupen64plus-video-gliden64") &&
                    !value.equals("libmupen64plus-video-gliden64.so"))
            {
                value = "libmupen64plus-video-gliden64.so";
            }
        }

        return value;
    }
}
