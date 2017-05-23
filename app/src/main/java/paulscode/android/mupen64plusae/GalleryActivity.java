/*
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
package paulscode.android.mupen64plusae;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.MenuItemCompat.OnActionExpandListener;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.json.JSONException;
import org.mupen64plusae.v3.fzurita.R;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import paulscode.android.mupen64plusae.GameSidebar.GameSidebarActionHandler;
import paulscode.android.mupen64plusae.billing.IabHelper;
import paulscode.android.mupen64plusae.billing.IabResult;
import paulscode.android.mupen64plusae.billing.Inventory;
import paulscode.android.mupen64plusae.billing.Purchase;
import paulscode.android.mupen64plusae.dialog.ConfirmationDialog;
import paulscode.android.mupen64plusae.dialog.ConfirmationDialog.PromptConfirmListener;
import paulscode.android.mupen64plusae.dialog.DynamicMenuDialogFragment;
import paulscode.android.mupen64plusae.dialog.Popups;
import paulscode.android.mupen64plusae.jni.CoreService;
import paulscode.android.mupen64plusae.persistent.AppData;
import paulscode.android.mupen64plusae.persistent.ConfigFile;
import paulscode.android.mupen64plusae.persistent.ConfigFile.ConfigSection;
import paulscode.android.mupen64plusae.persistent.GamePrefs;
import paulscode.android.mupen64plusae.persistent.GlobalPrefs;
import paulscode.android.mupen64plusae.task.ComputeMd5Task;
import paulscode.android.mupen64plusae.util.CountryCode;
import paulscode.android.mupen64plusae.util.FileUtil;
import paulscode.android.mupen64plusae.util.LocaleContextWrapper;
import paulscode.android.mupen64plusae.util.Notifier;
import paulscode.android.mupen64plusae.util.RomDatabase;
import paulscode.android.mupen64plusae.util.RomHeader;
import static paulscode.android.mupen64plusae.ActivityHelper.Keys.ROM_PATH;

public class GalleryActivity extends AppCompatActivity implements GameSidebarActionHandler, PromptConfirmListener,
        DynamicMenuDialogFragment.OnDynamicDialogMenuItemSelectedListener
{
    // Saved instance states
    private static final String STATE_QUERY = "query";
    private static final String STATE_SIDEBAR = "sidebar";
    private static final String STATE_CACHE_ROM_INFO_FRAGMENT= "STATE_CACHE_ROM_INFO_FRAGMENT";
    private static final String STATE_DONATION_ITEM = "STATE_DONATION_ITEM";
    private static final String STATE_EXTRACT_TEXTURES_FRAGMENT= "STATE_EXTRACT_TEXTURES_FRAGMENT";
    private static final String STATE_EXTRACT_ROM_FRAGMENT= "STATE_EXTRACT_ROM_FRAGMENT";
    private static final String STATE_GALLERY_REFRESH_NEEDED= "STATE_GALLERY_REFRESH_NEEDED";
    private static final String STATE_RESTART_CONFIRM_DIALOG = "STATE_RESTART_CONFIRM_DIALOG";
    private static final String STATE_CLEAR_CONFIRM_DIALOG = "STATE_CLEAR_CONFIRM_DIALOG";
    private static final String STATE_REMOVE_FROM_LIBRARY_DIALOG = "STATE_REMOVE_FROM_LIBRARY_DIALOG";
    private static final String STATE_DONATION_DIALOG = "STATE_DONATION_DIALOG";
    public static final int RESTART_CONFIRM_DIALOG_ID = 0;
    public static final int CLEAR_CONFIRM_DIALOG_ID = 1;
    public static final int REMOVE_FROM_LIBRARY_DIALOG_ID = 2;

    private static final int IAP_HELPER_REQUEST_CODE = 10;

    // App data and user preferences
    private AppData mAppData = null;
    private GlobalPrefs mGlobalPrefs = null;

    // Widgets
    private RecyclerView mGridView;
    private DrawerLayout mDrawerLayout = null;
    private ActionBarDrawerToggle mDrawerToggle;
    private MenuListView mDrawerList;
    private GameSidebar mGameSidebar;

    // Searching
    private SearchView mSearchView;
    private String mSearchQuery = "";

    // Resizable gallery thumbnails
    public int galleryWidth;
    public int galleryMaxWidth;
    public int galleryHalfSpacing;
    public int galleryColumns = 2;
    public float galleryAspectRatio;

    // Misc.
    private List<GalleryItem> mGalleryItems = null;
    private GalleryItem mSelectedItem = null;
    private boolean mDragging = false;

    private ScanRomsFragment mCacheRomInfoFragment = null;
    private ExtractTexturesFragment mExtractTexturesFragment = null;
    private ExtractRomFragment mExtractRomFragment = null;

    //True if the restart promp is enabled
    boolean mRestartPromptEnabled = true;

    //If this is set to true, the gallery will be refreshed next time this activity is resumed
    boolean mRefreshNeeded = false;

    //In-app purchases helper library
    IabHelper mIapHelper = null;

    // Selected donation item id
    String mSelectedDonationItem = null;

    @Override
    protected void onNewIntent( Intent intent )
    {
        // If the activity is already running and is launched again (e.g. from a file manager app),
        // the existing instance will be reused rather than a new one created. This behavior is
        // specified in the manifest (launchMode = singleTask). In that situation, any activities
        // above this on the stack (e.g. GameActivity, GamePrefsActivity) will be destroyed
        // gracefully and onNewIntent() will be called on this instance. onCreate() will NOT be
        // called again on this instance.
        super.onNewIntent( intent );

        // Only remember the last intent used
        setIntent( intent );

        // Get the ROM path if it was passed from another activity/app
        final Bundle extras = getIntent().getExtras();
        if( extras != null)
        {
            final String givenRomPath = extras.getString( ROM_PATH );

            if( !TextUtils.isEmpty( givenRomPath ) )
            {
                getIntent().removeExtra(ROM_PATH);
                launchGameOnCreation(givenRomPath);
            }
        }
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        if(TextUtils.isEmpty(LocaleContextWrapper.getLocalCode()))
        {
            super.attachBaseContext(newBase);
        }
        else
        {
            super.attachBaseContext(LocaleContextWrapper.wrap(newBase,LocaleContextWrapper.getLocalCode()));
        }
    }

    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
        super.onCreate( savedInstanceState );

        // Get app data and user preferences
        mAppData = new AppData( this );
        mGlobalPrefs = new GlobalPrefs( this, mAppData );

        // Get the ROM path if it was passed from another activity/app
        final Bundle extras = getIntent().getExtras();
        String givenRomPath = null;
        if( extras != null)
        {
            givenRomPath = extras.getString( ROM_PATH );
        }

        // Lay out the content
        setContentView( R.layout.gallery_activity );
        mGridView = (RecyclerView) findViewById( R.id.gridview );
        refreshGrid();

        // Add the toolbar to the activity (which supports the fancy menu/arrow animation)
        final Toolbar toolbar = (Toolbar) findViewById( R.id.toolbar );
        toolbar.setTitle( R.string.app_name );
        final View firstGridChild = mGridView.getChildAt(0);

        if(firstGridChild != null)
        {
            toolbar.setNextFocusDownId(firstGridChild.getId());
        }

        setSupportActionBar( toolbar );

        // Configure the navigation drawer
        mDrawerLayout = (DrawerLayout) findViewById( R.id.drawerLayout );
        mDrawerToggle = new ActionBarDrawerToggle( this, mDrawerLayout, toolbar, 0, 0 )
        {
            @Override
            public void onDrawerStateChanged( int newState )
            {
                // Intercepting the drawer open animation and re-closing it causes onDrawerClosed to
                // not fire,
                // So detect when this happens and wait until the drawer closes to handle it
                // manually
                if( newState == DrawerLayout.STATE_DRAGGING )
                {
                    // INTERCEPTED!
                    mDragging = true;
                    hideSoftKeyboard();
                }
                else if( newState == DrawerLayout.STATE_IDLE )
                {
                    if( mDragging && !mDrawerLayout.isDrawerOpen( GravityCompat.START ) )
                    {
                        // onDrawerClosed from dragging it
                        mDragging = false;
                        mDrawerList.setVisibility( View.VISIBLE );
                        mGameSidebar.setVisibility( View.GONE );
                        mSelectedItem = null;
                    }
                }
            }

            @Override
            public void onDrawerClosed( View drawerView )
            {
                // Hide the game information sidebar
                mDrawerList.setVisibility( View.VISIBLE );
                mGameSidebar.setVisibility( View.GONE );
                mGridView.requestFocus();

                if(mGridView.getAdapter().getItemCount() != 0)
                {
                    mGridView.getAdapter().notifyItemChanged(0);
                }

                mSelectedItem = null;

                super.onDrawerClosed( drawerView );
            }

            @Override
            public void onDrawerOpened( View drawerView )
            {
                hideSoftKeyboard();
                super.onDrawerOpened( drawerView );

                mDrawerList.requestFocus();
                mDrawerList.setSelection(0);
            }
        };
        mDrawerLayout.addDrawerListener( mDrawerToggle );

        // Configure the list in the navigation drawer
        mDrawerList = (MenuListView) findViewById( R.id.drawerNavigation );
        mDrawerList.setMenuResource( R.menu.gallery_drawer );

        //Remove touch screen profile configuration if in TV mode
        if(mGlobalPrefs.isBigScreenMode)
        {
            final MenuItem profileGroupItem = mDrawerList.getMenu().findItem(R.id.menuItem_profiles);
            profileGroupItem.getSubMenu().removeItem(R.id.menuItem_touchscreenProfiles);

            final MenuItem settingsGroupItem = mDrawerList.getMenu().findItem(R.id.menuItem_settings);
            settingsGroupItem.getSubMenu().removeItem(R.id.menuItem_categoryTouchscreen);
        }

        // Select the Library section
        mDrawerList.getMenu().getItem( 0 ).setChecked( true );

        // Handle menu item selections
        mDrawerList.setOnClickListener( new MenuListView.OnClickListener()
        {
            @Override
            public void onClick( MenuItem menuItem )
            {
                GalleryActivity.this.onOptionsItemSelected( menuItem );
            }
        } );

        // Configure the game information drawer
        mGameSidebar = (GameSidebar) findViewById( R.id.gameSidebar );

        // Handle events from the side bar
        mGameSidebar.setActionHandler(this, R.menu.gallery_game_drawer);

        if( savedInstanceState != null )
        {
            mSelectedItem = null;
            mSelectedDonationItem = null;
            final String md5 = savedInstanceState.getString( STATE_SIDEBAR );
            if( md5 != null )
            {
                // Repopulate the game sidebar
                for( final GalleryItem item : mGalleryItems )
                {
                    if( md5.equals( item.md5 ) )
                    {
                        onGalleryItemClick( item );
                        break;
                    }
                }
            }

            final String query = savedInstanceState.getString( STATE_QUERY );
            if( query != null )
                mSearchQuery = query;

            mRefreshNeeded = savedInstanceState.getBoolean(STATE_GALLERY_REFRESH_NEEDED);
            mSelectedDonationItem = savedInstanceState.getString(STATE_DONATION_ITEM);
        }

        // find the retained fragment on activity restarts
        final FragmentManager fm = getSupportFragmentManager();
        mCacheRomInfoFragment = (ScanRomsFragment) fm.findFragmentByTag(STATE_CACHE_ROM_INFO_FRAGMENT);
        mExtractTexturesFragment = (ExtractTexturesFragment) fm.findFragmentByTag(STATE_EXTRACT_TEXTURES_FRAGMENT);
        mExtractRomFragment = (ExtractRomFragment) fm.findFragmentByTag(STATE_EXTRACT_ROM_FRAGMENT);

        if(mCacheRomInfoFragment == null)
        {
            mCacheRomInfoFragment = new ScanRomsFragment();
            fm.beginTransaction().add(mCacheRomInfoFragment, STATE_CACHE_ROM_INFO_FRAGMENT).commit();
        }

        if(mExtractTexturesFragment == null)
        {
            mExtractTexturesFragment = new ExtractTexturesFragment();
            fm.beginTransaction().add(mExtractTexturesFragment, STATE_EXTRACT_TEXTURES_FRAGMENT).commit();
        }

        if(mExtractRomFragment == null)
        {
            mExtractRomFragment = new ExtractRomFragment();
            fm.beginTransaction().add(mExtractRomFragment, STATE_EXTRACT_ROM_FRAGMENT).commit();
        }

        // Set the sidebar opacity on the two sidebars
        mDrawerList.setBackground( new DrawerDrawable( mGlobalPrefs.displayActionBarTransparency ) );
        mGameSidebar.setBackground( new DrawerDrawable(mGlobalPrefs.displayActionBarTransparency ) );

        // Set up in-app purchases
        String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgb4" +
                "ebxzn0suhPrEWHZ/XpVeK25odQOSzClOQGq0AQmwdC6BscU5/a3zlTEs8G9sRsb34tAFo+oA" +
                "a14njRpHAJ+XCthtb9oSn0sN4zuNITlJAeyfGa22HVE+M7gwzCi1DF/Vf/DXujJ4IXXepdLdh" +
                "8iYYAxD8BQ9Gwg61IM7L620vRkIlkt7FWeNaHKtWEWnnT4ExksPaBdEyVEFGszOd5U3AhFkbI" +
                "Bu/xqo0nckTGM6yjAIJF8C3wAs0+9BBstuu+mVXJ5O+hKHi6HS/hcBQVzlfqTLbCHmEZRqjlSv" +
                "+fdy3WWDFi1d7cVzLVxUvIk2aQpK4Pjiiu4e3ZVxUuhOCYQIDAQAB";

        // compute your public key and store it in base64EncodedPublicKey
        mIapHelper = null;

        mIapHelper = new IabHelper(this, base64EncodedPublicKey);

        mIapHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
            public void onIabSetupFinished(IabResult result) {
                if (!result.isSuccess()) {
                    // Oh no, there was a problem.
                    Log.d("GalleryActivity", "Problem setting up In-app Billing: " + result);

                    GalleryActivity.this.mIapHelper = null;
                }
                // Hooray, IAB is fully set up!
            }
        });

        if( !TextUtils.isEmpty( givenRomPath ) )
        {
            getIntent().removeExtra(ROM_PATH);
            launchGameOnCreation(givenRomPath);
        }

        if(ActivityHelper.isServiceRunning(this, ActivityHelper.coreServiceName))
        {
            Log.i("GalleryActivity", "CoreService is running");

            Intent intent = new Intent(CoreService.SERVICE_EVENT);
            // You can also include some extra data.
            intent.putExtra("resume", true);
            sendBroadcast(intent);
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();

        //mRefreshNeeded will be set to true whenever a game is launched
        if(mRefreshNeeded)
        {
            mRefreshNeeded = false;
            refreshGrid();

            mGameSidebar.setVisibility( View.GONE );
            mDrawerList.setVisibility( View.VISIBLE );
        }
    }

    @Override
    public void onSaveInstanceState( Bundle savedInstanceState )
    {
        if( mSearchView != null )
            savedInstanceState.putString( STATE_QUERY, mSearchView.getQuery().toString() );
        if( mSelectedItem != null )
            savedInstanceState.putString( STATE_SIDEBAR, mSelectedItem.md5 );
        if( mSelectedDonationItem != null)
            savedInstanceState.putString( STATE_DONATION_ITEM, mSelectedDonationItem );

        savedInstanceState.putBoolean(STATE_GALLERY_REFRESH_NEEDED, mRefreshNeeded);

        super.onSaveInstanceState( savedInstanceState );
    }

    public void hideSoftKeyboard()
    {
        // Hide the soft keyboard if needed
        if( mSearchView == null )
            return;

        final InputMethodManager imm = (InputMethodManager) getSystemService( Context.INPUT_METHOD_SERVICE );
        imm.hideSoftInputFromWindow( mSearchView.getWindowToken(), 0 );
    }

    @Override
    protected void onPostCreate( Bundle savedInstanceState )
    {
        super.onPostCreate( savedInstanceState );
        mDrawerToggle.syncState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mIapHelper != null) try {
            mIapHelper.dispose();
        } catch (IabHelper.IabAsyncInProgressException e) {
            e.printStackTrace();
        }
        mIapHelper = null;
    }

    @Override
    public void onConfigurationChanged( Configuration newConfig )
    {
        super.onConfigurationChanged( newConfig );
        mDrawerToggle.onConfigurationChanged( newConfig );
    }

    @Override
    public boolean onCreateOptionsMenu( Menu menu )
    {
        getMenuInflater().inflate( R.menu.gallery_activity, menu );

        final MenuItem searchItem = menu.findItem( R.id.menuItem_search );
        MenuItemCompat.setOnActionExpandListener( searchItem, new OnActionExpandListener()
        {
            @Override
            public boolean onMenuItemActionCollapse( MenuItem item )
            {
                mSearchQuery = "";
                refreshGrid();
                return true;
            }

            @Override
            public boolean onMenuItemActionExpand( MenuItem item )
            {
                return true;
            }
        } );

        mSearchView = (SearchView) MenuItemCompat.getActionView( searchItem );
        mSearchView.setOnQueryTextListener( new OnQueryTextListener()
        {
            @Override
            public boolean onQueryTextSubmit( String query )
            {

                return false;
            }

            @Override
            public boolean onQueryTextChange( String query )
            {
                mSearchQuery = query;
                refreshGrid();
                return false;
            }
        } );

        if( !"".equals( mSearchQuery ) )
        {
            final String query = mSearchQuery;
            MenuItemCompat.expandActionView( searchItem );
            mSearchView.setQuery( query, true );
        }

        return super.onCreateOptionsMenu( menu );
    }

    private void launchGameOnCreation(String givenRomPath)
    {
        String finalRomPath = givenRomPath;

        boolean isZip = givenRomPath != null && givenRomPath.toLowerCase().endsWith("zip");

        if(isZip)
        {
            finalRomPath = ExtractFirstROMFromZip(givenRomPath);
        }

        if(finalRomPath != null)
        {
            // Asynchronously compute MD5 and launch game when finished
            final String computedMd5 = ComputeMd5Task.computeMd5( new File( finalRomPath ) );

            if(computedMd5 != null)
            {
                final RomHeader header = new RomHeader(finalRomPath);

                final RomDatabase database = RomDatabase.getInstance();

                if(!database.hasDatabaseFile())
                {
                    database.setDatabaseFile(mAppData.mupen64plus_ini);
                }

                final RomDatabase.RomDetail detail = database.lookupByMd5WithFallback( computedMd5, new File( finalRomPath), header.crc );
                String artPath = mGlobalPrefs.coverArtDir + "/" + detail.artName;

                launchGameActivity( finalRomPath, null, computedMd5, header.crc, header.name,
                        header.countryCode.getValue(), artPath, detail.goodName, false );
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected( MenuItem item )
    {
        switch (item.getItemId())
        {
        case R.id.menuItem_refreshRoms:
            ActivityHelper.startRomScanActivity(this);
            return true;
        case R.id.menuItem_library:
            mDrawerLayout.closeDrawer(GravityCompat.START);
            return true;
        case R.id.menuItem_categoryLibrary:
            mRefreshNeeded = true;
            ActivityHelper.startLibraryPrefsActivity( this );
            return true;
        case R.id.menuItem_categoryDisplay:
            mRefreshNeeded = true;
            ActivityHelper.startDisplayPrefsActivity( this );
            return true;
        case R.id.menuItem_categoryAudio:
            ActivityHelper.startAudioPrefsActivity( this );
            return true;
        case R.id.menuItem_categoryTouchscreen:
            mRefreshNeeded = true;
            ActivityHelper.startTouchscreenPrefsActivity( this );
            return true;
        case R.id.menuItem_categoryInput:
            ActivityHelper.startInputPrefsActivity( this );
            return true;
        case R.id.menuItem_categoryData:
            mRefreshNeeded = true;
            ActivityHelper.startDataPrefsActivity( this );
            return true;
        case R.id.menuItem_emulationProfiles:
            ActivityHelper.startManageEmulationProfilesActivity(this);
            return true;
        case R.id.menuItem_touchscreenProfiles:
            ActivityHelper.startManageTouchscreenProfilesActivity(this);
            return true;
        case R.id.menuItem_controllerProfiles:
            ActivityHelper.startManageControllerProfilesActivity(this);
            return true;
        case R.id.menuItem_faq:
            Popups.showFaq(this);
            return true;
        case R.id.menuItem_helpForum:
            ActivityHelper.launchUri(this, R.string.uri_forum);
            return true;
        case R.id.menuItem_controllerDiagnostics:
            ActivityHelper.startDiagnosticActivity(this);
            return true;
        case R.id.menuItem_reportBug:
            ActivityHelper.launchUri(this, R.string.uri_bugReport);
            return true;
        case R.id.menuItem_appVersion:
            Popups.showAppVersion(this);
            return true;
        case R.id.menuItem_logcat:
            Popups.showLogcat(this);
            return true;
        case R.id.menuItem_hardwareInfo:
            Popups.showHardwareInfo(this);
            return true;
        case R.id.menuItem_credits:
            ActivityHelper.launchUri(GalleryActivity.this, R.string.uri_credits);
            return true;
        case R.id.menuItem_localeOverride:
            mGlobalPrefs.changeLocale(this);
            return true;
        case R.id.menuItem_extract:
            ActivityHelper.starExtractTextureActivity(this);
            return true;
        case R.id.menuItem_clear:
        {
            String title = getString( R.string.confirm_title );
            String message = getString( R.string.confirmClearData_message );

            ConfirmationDialog confirmationDialog =
                    ConfirmationDialog.newInstance(CLEAR_CONFIRM_DIALOG_ID, title, message);

            FragmentManager fm = getSupportFragmentManager();
            confirmationDialog.show(fm, STATE_CLEAR_CONFIRM_DIALOG);
        }
            return true;
        case R.id.menuItem_donate: {

            if(mIapHelper != null)
                showInAppPurchases();
        }
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onGameSidebarAction(MenuItem menuItem)
    {
        final GalleryItem item = mSelectedItem;
        if( item == null )
            return;

        switch( menuItem.getItemId() )
        {
            case R.id.menuItem_resume:
                launchGameActivity( item.romFile.getAbsolutePath(),
                        item.zipFile == null ? null : item.zipFile.getAbsolutePath(),
                        item.md5, item.crc, item.headerName,
                        item.countryCode.getValue(), item.artPath, item.goodName, false );
                break;
            case R.id.menuItem_restart:
                //Don't show the prompt if this is the first time we start a game
                if(mRestartPromptEnabled)
                {
                    final CharSequence title = getText( R.string.confirm_title );
                    final CharSequence message = getText( R.string.confirmResetGame_message );

                    final ConfirmationDialog confirmationDialog =
                        ConfirmationDialog.newInstance(RESTART_CONFIRM_DIALOG_ID, title.toString(), message.toString());

                    final FragmentManager fm = getSupportFragmentManager();
                    confirmationDialog.show(fm, STATE_RESTART_CONFIRM_DIALOG);
                }
                else
                {
                    launchGameActivity( item.romFile.getAbsolutePath(),
                            item.zipFile == null ? null : item.zipFile.getAbsolutePath(),
                            item.md5, item.crc,
                            item.headerName, item.countryCode.getValue(), item.artPath,
                            item.goodName, true );
                }

                break;
            case R.id.menuItem_settings:
            {
                String romLegacySaveFileName = null;

                if(item.zipFile != null)
                {
                    romLegacySaveFileName = item.zipFile.getName();
                }
                else
                {
                    romLegacySaveFileName = item.romFile.getName();
                }
                ActivityHelper.startGamePrefsActivity( GalleryActivity.this, item.romFile.getAbsolutePath(),
                        item.md5, item.crc, item.headerName, item.goodName, item.countryCode.getValue(),
                        romLegacySaveFileName);
                break;

            }
            case R.id.menuItem_remove:
            {
                final CharSequence title = getText( R.string.confirm_title );
                final CharSequence message = getText( R.string.confirmRemoveFromLibrary_message );

                final ConfirmationDialog confirmationDialog =
                        ConfirmationDialog.newInstance(REMOVE_FROM_LIBRARY_DIALOG_ID, title.toString(), message.toString());

                final FragmentManager fm = getSupportFragmentManager();
                confirmationDialog.show(fm, STATE_REMOVE_FROM_LIBRARY_DIALOG);
            }
            default:
        }
    }

    @Override
    public void onPromptDialogClosed(int id, int which)
    {
        Log.i( "GalleryActivity", "onPromptDialogClosed" );

        if( which == DialogInterface.BUTTON_POSITIVE )
        {
            if(id == RESTART_CONFIRM_DIALOG_ID && mSelectedItem != null)
            {
                launchGameActivity( mSelectedItem.romFile.getAbsolutePath(),
                    mSelectedItem.zipFile == null ? null : mSelectedItem.zipFile.getAbsolutePath(),
                    mSelectedItem.md5, mSelectedItem.crc,
                    mSelectedItem.headerName, mSelectedItem.countryCode.getValue(), mSelectedItem.artPath,
                    mSelectedItem.goodName, true );
            }
            else if(id == CLEAR_CONFIRM_DIALOG_ID)
            {
                FileUtil.deleteFolder(new File(mGlobalPrefs.coreUserDataDir));
                FileUtil.deleteFolder(new File(mGlobalPrefs.coreUserCacheDir));
            }
            else if(id == REMOVE_FROM_LIBRARY_DIALOG_ID)
            {
                final ConfigFile config = new ConfigFile( mGlobalPrefs.romInfoCache_cfg );
                config.remove(mSelectedItem.md5);
                config.save();
                mDrawerLayout.closeDrawer( GravityCompat.START, false );
                refreshGrid();
            }
        }
    }

    public void onGalleryItemClick(GalleryItem item)
    {
        mSelectedItem = item;

        // Show the game info sidebar
        mDrawerList.setVisibility(View.GONE);
        mGameSidebar.setVisibility(View.VISIBLE);
        mGameSidebar.scrollTo(0, 0);

        // Set the cover art in the sidebar
        item.loadBitmap();
        mGameSidebar.setImage(item.artBitmap);

        // Set the game title
        mGameSidebar.setTitle(item.goodName);

        // If there are no saves for this game, disable the resume
        // option
        final String gameDataPath = GamePrefs.getGameDataPath(mSelectedItem.md5, mSelectedItem.headerName,
                mSelectedItem.countryCode.toString(), mAppData);
        final String autoSavePath = gameDataPath + "/" + GamePrefs.AUTO_SAVES_DIR + "/";

        final File autoSavePathFile = new File(autoSavePath);
        final File[] allFilesInSavePath = autoSavePathFile.listFiles();

        //No saves, go ahead and remove it
        final boolean visible = allFilesInSavePath != null && allFilesInSavePath.length != 0 &&
                mGlobalPrefs.maxAutoSaves > 0;

        if (visible)
        {
            // Restore the menu
            mGameSidebar.setActionHandler(GalleryActivity.this, R.menu.gallery_game_drawer);
            mRestartPromptEnabled = true;
        }
        else
        {
            // Disable the action handler
            mGameSidebar.getMenu().removeItem(R.id.menuItem_resume);

            final MenuItem restartItem = mGameSidebar.getMenu().findItem(R.id.menuItem_restart);
            restartItem.setTitle(getString(R.string.actionStart_title));
            restartItem.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_play));

            mGameSidebar.reload();
            mRestartPromptEnabled = false;
        }

        // Open the navigation drawer
        mDrawerLayout.openDrawer(GravityCompat.START);

        mGameSidebar.requestFocus();
        mGameSidebar.setSelection(0);
    }

    public boolean onGalleryItemLongClick( GalleryItem item )
    {
        launchGameActivity( item.romFile.getAbsolutePath(),
            item.zipFile == null ? null : item.zipFile.getAbsolutePath(),
            item.md5, item.crc, item.headerName, item.countryCode.getValue(),
            item.artPath, item.goodName, false );
        return true;
    }

    @Override
    public boolean onKeyDown( int keyCode, KeyEvent event )
    {
        if( keyCode == KeyEvent.KEYCODE_MENU )
        {
            // Show the navigation drawer when the user presses the Menu button
            // http://stackoverflow.com/q/22220275
            if( mDrawerLayout.isDrawerOpen( GravityCompat.START ) )
            {
                mDrawerLayout.closeDrawer( GravityCompat.START );
            }
            else
            {
                mDrawerLayout.openDrawer( GravityCompat.START );
            }
            return true;
        }
        return super.onKeyDown( keyCode, event );
    }

    @Override
    public void onBackPressed()
    {
        if( mDrawerLayout.isDrawerOpen( GravityCompat.START ) )
        {
            mDrawerLayout.closeDrawer( GravityCompat.START );
        }
        else
        {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Pass on the activity result to the helper for handling
        if(requestCode == IAP_HELPER_REQUEST_CODE)
        {
            mIapHelper.handleActivityResult(requestCode, resultCode, data);
        }
        // Check which request we're responding to
        else if (requestCode == ActivityHelper.SCAN_ROM_REQUEST_CODE) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK && data != null)
            {
                final Bundle extras = data.getExtras();
                final String searchPath = extras.getString( ActivityHelper.Keys.SEARCH_PATH );
                final boolean searchZips = extras.getBoolean( ActivityHelper.Keys.SEARCH_ZIPS );
                final boolean downloadArt = extras.getBoolean( ActivityHelper.Keys.DOWNLOAD_ART );
                final boolean clearGallery = extras.getBoolean( ActivityHelper.Keys.CLEAR_GALLERY );
                final boolean searchSubdirectories = extras.getBoolean( ActivityHelper.Keys.SEARCH_SUBDIR );

                if (searchPath != null)
                {
                    refreshRoms(new File(searchPath), searchZips, downloadArt, clearGallery, searchSubdirectories);
                }
            }
        }
        else if(requestCode == ActivityHelper.EXTRACT_TEXTURES_CODE)
        {
            // Make sure the request was successful
            if (resultCode == RESULT_OK && data != null)
            {
                final Bundle extras = data.getExtras();
                final String searchPath = extras.getString(ActivityHelper.Keys.SEARCH_PATH);

                if (searchPath != null)
                {
                    mExtractTexturesFragment.extractTextures(new File(searchPath));
                }
            }
        }
        else if(requestCode == ActivityHelper.GAME_ACTIVITY_CODE)
        {
            refreshGrid();

            if(!mGlobalPrefs.cacheRecentlyPlayed)
            {
                FileUtil.deleteFolder(new File(mGlobalPrefs.unzippedRomsDir));
            }
        }
    }

    private void refreshRoms(final File startDir, boolean searchZips, boolean downloadArt, boolean clearGallery, boolean searchSubdirectories)
    {
        mCacheRomInfoFragment.refreshRoms(startDir, searchZips, downloadArt, clearGallery, searchSubdirectories, mAppData, mGlobalPrefs);
    }

    void refreshGrid( ){

        //Reload global prefs
        mAppData = new AppData( this );
        mGlobalPrefs = new GlobalPrefs( this, mAppData );

        final ConfigFile config = new ConfigFile( mGlobalPrefs.romInfoCache_cfg );

        final String query = mSearchQuery.toLowerCase( Locale.US );
        String[] searches = null;
        if( query.length() > 0 )
            searches = query.split( " " );

        List<GalleryItem> items = new ArrayList<>();
        List<GalleryItem> recentItems = null;
        int currentTime = 0;
        if( mGlobalPrefs.isRecentShown )
        {
            recentItems = new ArrayList<>();
            currentTime = (int) ( new Date().getTime() / 1000 );
        }

        for( final String md5 : config.keySet() )
        {
            if( !ConfigFile.SECTIONLESS_NAME.equals( md5 ) )
            {
                final ConfigSection section = config.get( md5 );
                String goodName;
                if( mGlobalPrefs.isFullNameShown || !section.keySet().contains( "baseName" ) )
                    goodName = section.get( "goodName" );
                else
                    goodName = section.get( "baseName" );

                boolean matchesSearch = true;
                if( searches != null && searches.length > 0 && goodName != null)
                {
                    // Make sure the ROM name contains every token in the query
                    final String lowerName = goodName.toLowerCase( Locale.US );
                    for( final String search : searches )
                    {
                        if( search.length() > 0 && !lowerName.contains( search ) )
                        {
                            matchesSearch = false;
                            break;
                        }
                    }
                }

                if( matchesSearch && goodName != null)
                {
                    final String romPath = config.get( md5, "romPath" );
                    String zipPath = config.get( md5, "zipPath" );
                    final String artFullPath = config.get( md5, "artPath" );

                    //We get the file name to support the old gallery format
                    String artPath = !TextUtils.isEmpty(artFullPath) ? new File(artFullPath).getName() : null;

                    if(artPath != null)
                        artPath = mGlobalPrefs.coverArtDir + "/" + artPath;

                    String crc = config.get( md5, "crc" );
                    String headerName = config.get( md5, "headerName" );
                    final String countryCodeString = config.get( md5, "countryCode" );
                    CountryCode countryCode = CountryCode.UNKNOWN;

                    //We can't really do much if the rompath is null
                    if (romPath != null)
                    {
                        if (countryCodeString != null)
                        {
                            countryCode = CountryCode.getCountryCode(Byte.parseByte(countryCodeString));
                        }
                        final String lastPlayedStr = config.get(md5, "lastPlayed");

                        if (crc == null || headerName == null || countryCodeString == null)
                        {
                            final File file = new File(romPath);
                            final RomHeader header = new RomHeader(file);

                            crc = header.crc;
                            headerName = header.name;
                            countryCode = header.countryCode;

                            config.put(md5, "crc", crc);
                            config.put(md5, "headerName", headerName);
                            config.put(md5, "countryCode", Byte.toString(countryCode.getValue()));
                        }

                        int lastPlayed = 0;
                        if (lastPlayedStr != null)
                            lastPlayed = Integer.parseInt(lastPlayedStr);

                        final GalleryItem item = new GalleryItem(this, md5, crc, headerName, countryCode, goodName, romPath,
                            zipPath, artPath, lastPlayed, mGlobalPrefs.coverArtScale);
                        items.add(item);
                        boolean isNotOld = currentTime - item.lastPlayed <= 60 * 60 * 24 * 7; // 7 days
                        if (mGlobalPrefs.isRecentShown && isNotOld )
                        {
                            recentItems.add(item);
                        }

                        // Delete any old files that already exist inside a zip
                        // file
                        if (!isNotOld && !TextUtils.isEmpty(zipPath))
                        {
                            final File deleteFile = new File(romPath);

                            if(!deleteFile.isDirectory() && deleteFile.exists())
                            {
                                deleteFile.delete();
                            }
                        }
                    }
                }
            }
        }

        config.save();

        Collections.sort( items, new GalleryItem.NameComparator() );
        if( recentItems != null )
            Collections.sort( recentItems, new GalleryItem.RecentlyPlayedComparator() );

        List<GalleryItem> combinedItems = items;
        if( mGlobalPrefs.isRecentShown && recentItems.size() > 0 )
        {
            combinedItems = new ArrayList<GalleryItem>();

            combinedItems.add( new GalleryItem( this, getString( R.string.galleryRecentlyPlayed ) ) );
            combinedItems.addAll( recentItems );

            combinedItems.add( new GalleryItem( this, getString( R.string.galleryLibrary ) ) );
            combinedItems.addAll( items );

            items = combinedItems;
        }

        mGalleryItems = items;
        mGridView.setAdapter( new GalleryItem.Adapter( this, items ) );

        // Allow the headings to take up the entire width of the layout
        final List<GalleryItem> finalItems = items;
        final GridLayoutManager layoutManager = new GridLayoutManagerBetterScrolling( this, galleryColumns );
        layoutManager.setSpanSizeLookup( new GridLayoutManager.SpanSizeLookup()
        {
            @Override
            public int getSpanSize( int position )
            {
                // Headings will take up every span (column) in the grid
                if( finalItems.get( position ).isHeading )
                    return galleryColumns;

                // Games will fit in a single column
                return 1;
            }
        } );

        mGridView.setLayoutManager( layoutManager );

        // Update the grid layout
        galleryMaxWidth = (int) (getResources().getDimension( R.dimen.galleryImageWidth ) * mGlobalPrefs.coverArtScale);
        galleryHalfSpacing = (int) getResources().getDimension( R.dimen.galleryHalfSpacing );
        galleryAspectRatio = galleryMaxWidth * 1.0f
                / getResources().getDimension( R.dimen.galleryImageHeight )/mGlobalPrefs.coverArtScale;

        final DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics( metrics );

        final int width = metrics.widthPixels - galleryHalfSpacing * 2;
        galleryColumns = (int) Math
                .ceil( width * 1.0 / ( galleryMaxWidth + galleryHalfSpacing * 2 ) );
        galleryWidth = width / galleryColumns - galleryHalfSpacing * 2;

        layoutManager.setSpanCount( galleryColumns );
        mGridView.getAdapter().notifyDataSetChanged();
        mGridView.setFocusable(false);
        mGridView.setFocusableInTouchMode(false);
    }

    public void launchGameActivity( String romPath, String zipPath, String romMd5, String romCrc,
            String romHeaderName, byte romCountryCode, String romArtPath, String romGoodName, boolean isRestarting)
    {
        Log.i( "GalleryActivity", "launchGameActivity" );

        // Make sure that the storage is accessible
        if( !mAppData.isSdCardAccessible() )
        {
            Log.e( "GalleryActivity", "SD Card not accessible" );
            Notifier.showToast( this, R.string.toast_sdInaccessible );

            mAppData.putAssetVersion(0);
            ActivityHelper.startSplashActivity(this);
            finish();
            return;
        }

        // Update the ConfigSection with the new value for lastPlayed
        final String lastPlayed = Integer.toString( (int) ( new Date().getTime() / 1000 ) );
        final ConfigFile config = new ConfigFile( mGlobalPrefs.romInfoCache_cfg );
        File romFileName = new File(romPath);

        String romLegacySaveFileName;

        //Convoluted way of moving legacy save file names to the new format
        if(zipPath != null)
        {
            File zipFile = new File(zipPath);
            romLegacySaveFileName = zipFile.getName();
        }
        else
        {
            File romFile = new File(romPath);
            romLegacySaveFileName = romFile.getName();
        }


        config.put(romMd5, "lastPlayed", lastPlayed);
        config.save();

        ///Drawer layout can be null if this method is called from onCreate
        if (mDrawerLayout != null) {
            //Close drawer without animation
            mDrawerLayout.closeDrawer(GravityCompat.START, false);
        }
        mRefreshNeeded = true;

        mSelectedItem = null;

        if(romFileName.exists())
        {
            // Notify user that the game activity is starting
            Notifier.showToast(this, R.string.toast_launchingEmulator);

            // Launch the game activity
            ActivityHelper.startGameActivity(this, romPath, romMd5, romCrc, romHeaderName, romCountryCode,
                    romArtPath, romGoodName, romLegacySaveFileName, isRestarting);
        }
        else
        {
            if( config.get(romMd5) != null)
            {
                if(!TextUtils.isEmpty(zipPath))
                {
                    mExtractRomFragment.ExtractRom(zipPath, mGlobalPrefs.unzippedRomsDir, romPath, romMd5, romCrc,
                            romHeaderName, romCountryCode, romArtPath, romGoodName, romLegacySaveFileName,
                            isRestarting);
                }
            }
        }
    }

    private String ExtractFirstROMFromZip(String zipPath)
    {
        try
        {
            ZipFile zipFile = new ZipFile( zipPath );
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while( entries.hasMoreElements() )
            {
                ZipEntry zipEntry = entries.nextElement();

                try
                {
                    InputStream zipStream = zipFile.getInputStream( zipEntry );
                    File extractedFile = FileUtil.extractRomFile( new File( mGlobalPrefs.unzippedRomsDir ), zipEntry, zipStream );

                    if( extractedFile != null)
                    {
                        zipStream.close();
                        return extractedFile.getPath();
                    }
                }
                catch( IOException e )
                {
                    Log.w( "CacheRomInfoService", e );
                }
            }
            zipFile.close();
        }
        catch( IOException|ArrayIndexOutOfBoundsException e )
        {
            Log.w( "GalleryActivity", e );
        }

        return null;
    }

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event)
    {
        //Nothing to do
        return false;
    }

    @Override
    public void onPrepareMenuList(MenuListView listView)
    {

    }

    private void showInAppPurchases()
    {
        ArrayList<String> donationOptions = new ArrayList<String>();
        donationOptions.add("one_dollar");
        donationOptions.add("three_dollar");
        donationOptions.add("five_dollar");
        donationOptions.add("ten_dollar");

        ArrayList<String> donationPrices = new ArrayList<String>();

        for(String optionIds : donationOptions)
        {
            try {
                String price = mIapHelper.getPricesDev("org.mupen64plusae.v3.fzurita", optionIds);

                donationPrices.add(price != null ? price : getString(R.string.galleryDonationValueNotAvailable));
            } catch (RemoteException|JSONException e) {
                donationPrices.add(getString(R.string.galleryDonationValueNotAvailable));
            }
        }

        DynamicMenuDialogFragment menuDialogFragment = DynamicMenuDialogFragment.newInstance(0,
                getString(R.string.galleryDonateDialogTitle), donationOptions, donationPrices);

        FragmentManager fm = getSupportFragmentManager();
        menuDialogFragment.show(fm, STATE_DONATION_DIALOG);
    }

    @Override
    public void onDialogMenuItemSelected(int dialogId, String itemId)
    {
        // Have we been disposed of in the meantime? If so, quit.
        if (mIapHelper == null) return;

        mSelectedDonationItem = itemId;

        // Query inventory to make sure everything is consumed
        Log.d("GalleryActivity", "Setup successful. Querying inventory.");
        try {
            mIapHelper.queryInventoryAsync(mGotInventoryListener);
        } catch (IabHelper.IabAsyncInProgressException e) {
            Log.d("GalleryActivity", "Error querying inventory. Another async operation in progress.");
        }
    }


    // Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {

            // if we were disposed of in the meantime, quit.
            if (mIapHelper == null) return;

            if (result.isFailure()) {
                runOnUiThread( new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Notifier.showToast( GalleryActivity.this, getString(R.string.galleryDonationError) );
                    }
                } );
                return;
            }

            try {
                mIapHelper.consumeAsync(purchase, mConsumeFinishedListener);
            } catch (IabHelper.IabAsyncInProgressException e) {
                return;
            }
        }
    };

    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {

            // if we were disposed of in the meantime, quit.
            if (mIapHelper == null) return;

            runOnUiThread( new Runnable()
            {
                @Override
                public void run()
                {
                    Notifier.showToast( GalleryActivity.this, getString(R.string.galleryDonationThankYou) );
                }
            } );

            // Query inventory to make sure everything is consumed
            Log.d("GalleryActivity", "Setup successful. Querying inventory.");
            try {
                mIapHelper.queryInventoryAsync(mGotInventoryListener);
            } catch (IabHelper.IabAsyncInProgressException e) {
                Log.d("GalleryActivity", "Error querying inventory. Another async operation in progress.");
            }
        }
    };

    // Listener that's called when we finish querying the items and subscriptions we own
    IabHelper.QueryInventoryFinishedListener mGotInventoryListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            Log.d("GalleryActivity", "Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mIapHelper == null) return;

            if (result.isFailure()) {
                runOnUiThread( new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Notifier.showToast( GalleryActivity.this, getString(R.string.galleryDonationError) );
                    }
                } );
                return;
            }

            Log.d("GalleryActivity", "Query inventory was successful.");

            ArrayList<String> donationOptions = new ArrayList<String>();
            donationOptions.add("android.test.purchased");
            donationOptions.add("one_dollar");
            donationOptions.add("three_dollar");
            donationOptions.add("five_dollar");
            donationOptions.add("ten_dollar");

            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            Purchase donationPurchase = null;
            int index = 0;
            String foundDonation = null;

            //Find an item already purchased and consume it
            while(donationPurchase == null && index < donationOptions.size())
            {
                donationPurchase = inventory.getPurchase(donationOptions.get(index));

                if(donationPurchase != null)
                {
                    foundDonation = donationOptions.get(index);
                }

                ++index;
            }
            // Check for gas delivery -- if we own gas, we should fill up the tank immediately


            if (donationPurchase != null) {
                try {
                    mIapHelper.consumeAsync(inventory.getPurchase(foundDonation), mConsumeFinishedListener);
                } catch (IabHelper.IabAsyncInProgressException e) {
                    Log.d("GalleryActivity", "Error quering inventory");
                }
                return;
            }
            else if (mSelectedDonationItem != null)
            {
                runOnUiThread( new Runnable()
                {
                    @Override
                    public void run()
                    {
                    try {
                        mIapHelper.launchPurchaseFlow(GalleryActivity.this, mSelectedDonationItem, IAP_HELPER_REQUEST_CODE,
                                mPurchaseFinishedListener, "");
                    } catch (IabHelper.IabAsyncInProgressException e) {
                        Log.d("GalleryActivity","Error launching purchase flow. Another async operation in progress.");
                    }

                    mSelectedDonationItem = null;
                    }
                } );
            }
        }
    };
}

