/*
 * 
 */

package org.abrantix.rockon.rockonnggl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

public class RockOnCubeRenderer extends RockOnRenderer implements GLSurfaceView.Renderer{

	final String TAG = "RockOnCubeRenderer";
	
	public void renderNow(){
		mRequestRenderHandler.sendEmptyMessage(0);
	}
	
    public RockOnCubeRenderer(Context context, Handler requestRenderHandler) {
        mContext = context;
        mRequestRenderHandler = requestRenderHandler;
        
    	initNonGlVars(context, false);
    }
    
    public void changePlaylist(int playlistId){
    	mPositionY = 0;
    	mTargetPositionY = 0;
    	mPositionX = 0;
    	mTargetPositionX = 0;
    	initNonGlVars(mContext, true);
    	this.triggerPositionUpdate();
    }
    
    private void initNonGlVars(Context context, boolean force){
    	
    	/** init album cursor **/
    	if(mAlbumCursor == null || force){
    		CursorUtils cursorUtils = new CursorUtils(context);
    		mAlbumCursor = 
    			cursorUtils.getAlbumListFromPlaylist(
    				PreferenceManager.getDefaultSharedPreferences(mContext).
    					getInt(
    							Constants.prefkey_mPlaylistId,
    							Constants.PLAYLIST_ALL));
    		Log.i(TAG, "CREATED ABUM CURSOR");
    		if(mAlbumCursor == null)
    			Log.i(TAG, "ALBUM CURSOR IS NULL");
    	}
    	
    	/** init dimensions */
    	mBitmapWidth = Constants.ALBUM_ART_TEXTURE_SIZE;
    	mBitmapHeight = Constants.ALBUM_ART_TEXTURE_SIZE;
    	
    	/** albumNavUtils */
    	mAlbumNavItemUtils = new AlbumNavItemUtils(mBitmapWidth, mBitmapHeight);
    	
    	/** init cover bitmap cache */
    	for(int i = 0; i < mCacheSize; i++){
    		mAlbumNavItem[i] = new AlbumNavItem();
        	mAlbumNavItem[i].index = -1;
    		mAlbumNavItem[i].cover = Bitmap.createBitmap(
    				mBitmapWidth, 
    				mBitmapHeight, 
    				Bitmap.Config.RGB_565);
    		mAlbumNavItem[i].label = Bitmap.createBitmap(
    				mBitmapWidth,
    				mBitmapHeight/4,
    				Bitmap.Config.ARGB_8888);
    	}
    	mColorComponentBuffer = new byte[4*mBitmapWidth*mBitmapHeight];
    	
    	/** init alphabet bitmap cache */
    	for(int i = 0; i < mCacheSize; i++){
    		mAlphabetNavItem[i] = new AlphabetNavItem();
    		mAlphabetNavItem[i].letter = -1;
    		mAlphabetNavItem[i].letterBitmap = Bitmap.createBitmap(
    				mBitmapWidth, 
    				mBitmapHeight, 
    				Bitmap.Config.ARGB_8888);
    	}
    }

    public	Cursor getAlbumCursor(){
    	return mAlbumCursor;
    }
    
    public int[] getConfigSpec() {
//        if (mTranslucentBackground) {
                // We want a depth buffer and an alpha buffer
                int[] configSpec = {
                        EGL10.EGL_RED_SIZE,      8,
                        EGL10.EGL_GREEN_SIZE,    8,
                        EGL10.EGL_BLUE_SIZE,     8,
                        EGL10.EGL_ALPHA_SIZE,    8,
                        EGL10.EGL_DEPTH_SIZE,   16,
                        EGL10.EGL_NONE
                };
                return configSpec;
//            } else {
//                // We want a depth buffer, don't care about the
//                // details of the color buffer.
//                int[] configSpec = {
//                        EGL10.EGL_DEPTH_SIZE,   16,
//                        EGL10.EGL_NONE
//                };
//                return configSpec;
//            }
    }
    
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
//    	Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

    	
        /*
         * By default, OpenGL enables features that improve quality
         * but reduce performance. One might want to tweak that
         * especially on software renderer.
         */
        gl.glDisable(GL10.GL_DITHER);

        /*
         * Some one-time OpenGL initialization can be made here
         * probably based on features of this particular context
         */
        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT,
                GL10.GL_FASTEST);

        gl.glClearColor(0.f, 0.f, 0.f, 0);
//        gl.glClearColor(.5f, .5f, .5f, 1);
        gl.glShadeModel(GL10.GL_SMOOTH);
        gl.glEnable(GL10.GL_DEPTH_TEST);
        gl.glEnable(GL10.GL_TEXTURE_2D);

        /*
         * Create our textures. This has to be done each time the
         * surface is created.
         */
        // album covers - vertical
        gl.glGenTextures(mTextureId.length, mTextureId, 0);
        // album labels
        gl.glGenTextures(mTextureLabelId.length, mTextureLabelId, 0);
        // album labels
        gl.glGenTextures(mTextureAlphabetId.length, mTextureAlphabetId, 0);

        mRockOnCube = new RockOnCube(mTextureId, mTextureAlphabetId);
        mAlbumLabelGlText = new AlbumLabelGlText(mTextureLabelId[0]);
        
        
        /*
         * By default, OpenGL enables features that improve quality
         * but reduce performance. One might want to tweak that
         * especially on software renderer.
         */
        gl.glDisable(GL10.GL_DITHER);
        gl.glEnable(GL10.GL_DEPTH_TEST);
//        gl.glEnable(GL10.GL_LINE_SMOOTH);
//        gl.glEnable(GL10.GL_LINE_SMOOTH_HINT);
//        gl.glEnable(GL10.GL_BLEND);
//        gl.glBlendFunc(
//        		GL10.GL_ONE, 
//        		GL10.GL_ONE_MINUS_SRC_ALPHA);
//        		GL10.GL_ONE_MINUS_SRC_ALPHA);
//        gl.glHint(GL10.GL_LINE_SMOOTH_HINT, GL10.GL_NICEST);
        
        gl.glTexEnvx(
        		GL10.GL_TEXTURE_ENV, 
        		GL10.GL_TEXTURE_ENV_MODE,
                GL10.GL_MODULATE);

        /** FOG */
        gl.glEnable(GL10.GL_FOG);
        gl.glFogx(GL10.GL_FOG_MODE, GL10.GL_LINEAR);
//        gl.glFogx(GL10.GL_FOG_MODE, GL10.GL_EXP); // GL_EXP2 doesnt show anything
        gl.glFogf(GL10.GL_FOG_START, 4.5f);
        gl.glFogf(GL10.GL_FOG_END, 5.5f);
//        float[] fogColor = {.5f,.5f,.5f, 1.f};
//        gl.glFogfv(GL10.GL_FOG_COLOR, FloatBuffer.wrap(fogColor));
        gl.glHint(GL10.GL_FOG_HINT, GL10.GL_NICEST);
    }

    public int getItemDimension(){
    	return (int)(mHeight * .8f);
    }
    
    /* optmization */
    float	distanceToRotationLimits = 0.f;
    public void onDrawFrame(GL10 gl) {
    	  
    	
    	/** Calculate new position */
    	if(!updatePosition(false)){
    		
    	} 
  
    	mRockOnCube.setHorizontalIndex((int) mPositionX, mPositionX);
    	mRockOnCube.setVerticalIndex((int) mPositionY, mPositionY);
    	
        /*
         * Usually, the first thing one might want to do is to clear
         * the screen. The most efficient way of doing this is to use
         * glClear().
         */

        gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        /*
         * Now we're ready to draw some 3D objects
         */

        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadIdentity();

        /* 
         * Check if we are doing a click animation
         * 	- it changes the eye spot of the camera
         */
        if(mClickAnimation){
        	/* camera eye update */
        	mEyeX += updateFraction * Constants.SCROLL_SPEED_SMOOTHNESS * (mEyeTargetX-mEyeX);
        	mEyeY += updateFraction * Constants.SCROLL_SPEED_SMOOTHNESS * (mEyeTargetY-mEyeY);
        	mEyeZ += updateFraction * Constants.SCROLL_SPEED_SMOOTHNESS * (mEyeTargetZ-mEyeZ);
        	/* minimum movements */
        	if(Math.abs(mEyeTargetX - mEyeX) * Constants.SCROLL_SPEED_SMOOTHNESS * updateFraction < Constants.MIN_SCROLL)
        		mEyeX +=  
        			Math.signum(mEyeTargetX - mEyeX) * 
        				Math.min(
        					Math.abs(mEyeTargetX - mEyeX),
        					Constants.MIN_SCROLL);
        	if(Math.abs(mEyeTargetY - mEyeY) * Constants.SCROLL_SPEED_SMOOTHNESS * updateFraction < Constants.MIN_SCROLL)
        		mEyeY += 
        			Math.signum(mEyeTargetY - mEyeY) * 
        				Math.min(
        						Math.abs(mEyeTargetY - mEyeY),
        						Constants.MIN_SCROLL);
        	if(Math.abs(mEyeTargetZ - mEyeZ) * Constants.SCROLL_SPEED_SMOOTHNESS * updateFraction < Constants.MIN_SCROLL)
        		mEyeZ += 
        			Math.signum(mEyeTargetZ - mEyeZ) * 
        				Math.min(
        						Math.abs(mEyeTargetZ - mEyeZ),
        						Constants.MIN_SCROLL);
        	/* end of animation */
//        	Log.i(TAG, "X: "+mEyeX+" - "+mEyeTargetX);
//        	Log.i(TAG, "Y: "+mEyeY+" - "+mEyeTargetY);
//        	Log.i(TAG, "Z: "+mEyeZ+" - "+mEyeTargetZ);
        	if(mEyeX == mEyeTargetX && mEyeY == mEyeTargetY && mEyeZ == mEyeTargetZ)
        		mClickAnimation = false;
        }
        else
        {
            /* move camera when scrolling cube in Y axis */
        	if(mPositionY != mTargetPositionY)
        	{
        		distanceToRotationLimits = 
        			Math.max(
        				Math.min(
	            			.5f 
	            			* 
	            			(Math.min(
	            					Math.abs(mPositionY - mTargetPositionY),
	            					Math.abs(mPositionY - mRotationInitialPositionY))
	            					-1),
	            			1.5f),
	            		0.f);
	            			
        		// adjust our 'eye'
	            mEyeZ = 
	            	-5.f 
	            	- 
	            	distanceToRotationLimits;
	            // adjust the fog
	            gl.glFogf(
	            		GL10.GL_FOG_START, 
	            		4.5f + .5f * distanceToRotationLimits);
	            gl.glFogf(
	            		GL10.GL_FOG_END, 
	            		5.5f + distanceToRotationLimits);
        	}
        }
               
        GLU.gluLookAt(gl, mEyeX, mEyeY, mEyeZ, 0f, 0f, 0f, 0f, -1.0f, 0.0f);

        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        
        /* Center (or at least compensate) in the X axis */
        gl.glTranslatef(.1f, 0.f, 0.f);
        
        /* Calculate rotations */
        if(mPositionX != 0)
	        rotationAngleX = 
	        	((flooredPositionX % mCacheSize) + positionOffsetX)
	        	/
	        	4.f
	        	*
	        	360.f;
        else
        	rotationAngleX = 0;
        
        rotationAngleY = 
        	((flooredPositionY % mCacheSize) + positionOffsetY)
        	/
        	(float) mCacheSize
        	*
        	360.f;
                
        if(mScrollMode == Constants.SCROLL_MODE_VERTICAL){
        	gl.glRotatef(rotationAngleX, 0.f, 1.f, 0.f);
        	gl.glRotatef(-rotationAngleY, 1.f, 0.f, 0.f);
        }
        
        /* update textures if needed -- whenever we cross one album */
        texturesUpdated = updateTextures(gl);
                
    	/* 
    	 * ??
    	 */
//        if(texturesUpdated)
//	        gl.glTexEnvx(
//	        		GL10.GL_TEXTURE_ENV, 
//	        		GL10.GL_TEXTURE_ENV_MODE,
//	                GL10.GL_MODULATE);	        
        
//        if(!texturesUpdated){	// avoids a strange flickering...
//        	mRockOnCube.draw(gl);
        
        /* draw cube */
    	mRockOnCube.draw(gl);
    	
    	/* draw label */
    	if(Math.abs(mPositionY - mTargetPositionY) < .5f &&
    		mPositionX > -1.f &&
    		mPositionX < 1.f)
    	{
        	gl.glLoadIdentity();
            GLU.gluLookAt(gl, mEyeX, mEyeY, mEyeZ, 0f, 0f, 0f, 0f, -1.0f, 0.0f);
            gl.glTranslatef(.1f, -1.55f, -1.f);
            // X rotation
            gl.glRotatef(rotationAngleX, 0.f, 1.f, 0.f);
            // Y rotation
	        gl.glRotatef((.5f - Math.abs(positionOffsetY-0.5f))*2*90.f, 1.f, 0.f, 0.f);
	        tmpTextureIdx = (int)Math.round(mPositionY%mTextureId.length);
        	if(tmpTextureIdx >= mTextureId.length || 
        		tmpTextureIdx < 0)
            	mAlbumLabelGlText.setTexture(mTextureLabelId[0]);
            else
            	mAlbumLabelGlText.setTexture(mTextureLabelId[tmpTextureIdx]);
        	mAlbumLabelGlText.draw(gl);
//        	}
        }
        if(mTargetPositionX != mPositionX ||
        	mPositionX != 0 ||
        	mTargetPositionY != mPositionY || 
        	mClickAnimation ||
        	texturesUpdated)
        	renderNow();
    }

    public void onSurfaceChanged(GL10 gl, int w, int h) {
        gl.glViewport(0, 0, w, h);

        mHeight = h;
        mWidth = w;
        
        /*
        * Set our projection matrix. This doesn't have to be done
        * each time we draw, but usually a new projection needs to
        * be set when the viewport is resized.
        */

        float ratio = (float) w / h;
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glFrustumf(-ratio, ratio, -1, 1, 3, 20);

    }

    void showClickAnimation(float x, float y){
    	this.mClickAnimation = true;
    	// this should be in the constants -- too lazy
    	this.mEyeTargetX = .75f;
    	this.mEyeTargetY = -2.f;
    	this.mEyeTargetZ = -5.75f;
    	pTimestamp = System.currentTimeMillis();
    	this.renderNow();
    }
    
    void reverseClickAnimation(){
    	this.mClickAnimation = true;
    	// this should be in the constants -- too lazy
    	this.mEyeTargetX = .75f;
    	this.mEyeTargetY = -2.f;
    	this.mEyeTargetZ = -5.f;
    	pTimestamp = System.currentTimeMillis();
    	this.renderNow();
    }
    
    int getClickActionDelay(){
    	return Constants.CLICK_ACTION_DELAY;
    }
    
    void forceTextureUpdateOnNextDraw(){
    	mForceTextureUpdate = true;
    	this.renderNow();
    }
    
    int		albumIndexTmp;
    int		alphabetIndexTmp;
    int		lastInitial = -1;
    int		charTmp;
    boolean changed;
    private boolean updateTextures(GL10 gl){
    	changed = false;
    	if(mAlbumCursor != null){
	    	/* Album Cover textures in vertical scrolling */
    		for(int i = 0; i < mCacheSize; i++){
	    		albumIndexTmp = (int) (Math.floor((float) flooredPositionY / (float) mCacheSize) * mCacheSize + i);
	    		if(albumIndexTmp - flooredPositionY > 2){
	    			albumIndexTmp -= mCacheSize;
	    		} else if (albumIndexTmp - flooredPositionY < -1){
	    			albumIndexTmp += mCacheSize;
	    		}
	    		
	//    		Log.i(TAG, 
//	    			"albumIndexTmp: "+albumIndexTmp+
//	    			" flooredPosition: "+flooredPosition+
//	    			" mPosition: "+mPosition);
	    		
	    		if(setupAlbumTextures(gl, i, albumIndexTmp, mForceTextureUpdate))
	    			changed = true;
	    	}
	    	
    		/* Alphabetical textures in horizontal scrolling of the cube */
    		if(mPositionX != 0){
    			for(int i=0; i<mTextureAlphabetId.length; i++){    	    		
    				if(lastInitial == -1){
    					/** 
    					 * FIXME: quick fix for bug, untested, unchecked
    					 */
    					if(flooredPositionY > mAlbumCursor.getCount() - 1)
						{
    						mAlbumCursor.moveToLast();
    						Log.i(TAG, "Renderer album cursor overflow XXX FIXME");
						} else {
							mAlbumCursor.moveToPosition(flooredPositionY);
						}
    					lastInitial = 
    						mAlbumCursor.
    							getString(
    									mAlbumCursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST)).
    							toLowerCase().
    								charAt(0);
    					if(lastInitial < 'a')
    						lastInitial = 'a' - 1;
    				}
    				
//    				Log.i(TAG, "flooredX: "+flooredPositionX);
//    				Log.i(TAG, "lastInitial: "+(char)lastInitial);
//    				Log.i(TAG, "flooredPositionX: "+flooredPositionX+" (int)/mCacheSize: "+(int)((flooredPositionX)/mCacheSize));
//    				Log.i(TAG, "flooredPositionX: "+flooredPositionX+" %mCacheSize: "+(flooredPositionX)%mCacheSize);
    				
    				charTmp = 
    					lastInitial + 
    					(int)((flooredPositionX)/mCacheSize)*mCacheSize + 
    					i;
	    				
	    			/**
	    			 * Transition special cases
	    			 */
	    			/* cache turn point when going forward in the alphabet */
    				if(flooredPositionX%mCacheSize == mCacheSize - 1 &&
	    				i == 0)
	    			{
	    				charTmp += mCacheSize;
	    			}
	    			
	    			/* negative scrolling (going back in the alphabet) */
	    			if(flooredPositionX%mCacheSize < 0 &&
	    				i != 0)
	    			{
	    				charTmp -= mCacheSize;
	    			}


    	    		if(setupAlphabetTextures(gl, i, charTmp, mForceTextureUpdate))
    	    			changed = true;
    	    		
    	    		/* DEBUG CODE */
//    				undefined.eraseColor(Color.CYAN);
//    	    		bindTexture(gl, undefined, mTextureAlphabetId[i]);
    			}
    		}
	    	
	    	if(mForceTextureUpdate)
	    		mForceTextureUpdate = false;
    	}
    	return changed;
    }
    
    Bitmap undefined = Bitmap.createBitmap(
			Constants.REASONABLE_ALBUM_ART_SIZE, 
			Constants.REASONABLE_ALBUM_ART_SIZE, 
			Bitmap.Config.RGB_565);
    
    private boolean setupAlbumTextures(GL10 gl, int cacheIndex, int albumIndex, boolean force){
    	/** texture needs update? */
    	if(mAlbumNavItem[cacheIndex].index != albumIndex || force){
	    	
//    		Log.i(TAG, "albumIndexTmp: "+albumIndexTmp+" flooredPosition: "+flooredPosition+" mPosition: "+mPosition);

    		/** Update cache item */
    		mAlbumNavItem[cacheIndex].index = albumIndex;
    		if(albumIndex < 0 || albumIndex >= mAlbumCursor.getCount())
    		{
    			mAlbumNavItem[cacheIndex].albumName = "";
    			mAlbumNavItem[cacheIndex].artistName = "";
    			mAlbumNavItem[cacheIndex].cover = undefined;
    			mAlbumNavItem[cacheIndex].cover.eraseColor(Color.argb(127, 122, 122, 0));
    			// we cannot change the bitmap reference of the item
    			// we need to write to the existing reference
    			mAlbumNavItemUtils.fillAlbumLabel(
    					mAlbumNavItem[cacheIndex],
    					mBitmapWidth, 
    					mBitmapHeight/4);
    		} 
    		else 
    		{
	    		if(!mAlbumNavItemUtils.fillAlbumInfo(
	    				mAlbumCursor, 
	    				mAlbumNavItem[cacheIndex], 
	    				albumIndex))
	    		{
	    			mAlbumNavItem[cacheIndex].albumName = null;
	    			mAlbumNavItem[cacheIndex].artistName = null;
	    			mAlbumNavItem[cacheIndex].albumKey = null;
	    		}
	    		if(!mAlbumNavItemUtils.fillAlbumBitmap(
	    				mAlbumNavItem[cacheIndex], 
	    				mBitmapWidth, 
	    				mBitmapHeight, 
	    				mColorComponentBuffer))
	    		{
	//    			mAlbumNavItem[cacheIndex].cover = null;
	//    			mAlbumNavItem[cacheIndex].cover = 
	//    				Bitmap.createBitmap(
	//    						mBitmapWidth, 
	//    						mBitmapHeight, 
	//    						Bitmap.Config.RGB_565);
	    			mAlbumNavItem[cacheIndex].cover = undefined;
	    			mAlbumNavItem[cacheIndex].cover.eraseColor(Color.argb(127, 0, 255, 0));
	
	    		}
	    		if(Math.abs(mTargetPositionY - mPositionY) < 3 ||
	    				mPositionY < 3){ // avoid unnecessary processing
		    		if(!mAlbumNavItemUtils.fillAlbumLabel(
		    				mAlbumNavItem[cacheIndex],
		    				mBitmapWidth,
		    				mBitmapHeight/4))
		    		{
	//	    			mAlbumNavItem[cacheIndex].label = undefined;
	//	    			mAlbumNavItem[cacheIndex].label.eraseColor(Color.argb(0, 0, 0, 0));
		    		}
	    		}
    		}
    		
//    		Log.i(TAG, "cacheIndex: "+cacheIndex+"");
    		
	    	/** bind new texture */
    		bindTexture(gl, mAlbumNavItem[cacheIndex].cover, mTextureId[cacheIndex]);
    		bindTexture(gl, mAlbumNavItem[cacheIndex].label, mTextureLabelId[cacheIndex]);
    		
    		return true;
    	} else  {
    		return false;
    	}
    	
    }
    
    private boolean setupAlphabetTextures(GL10 gl, int cacheIndex, int letter, boolean force){
//		Log.i(TAG, "letter: "+(char)letter);

    	/** precheck albumNavItem */
    	if(mAlphabetNavItem[cacheIndex].letter != letter || force){
	    	
//    		Log.i(TAG, " + letter: "+(char)letter);

    		/** Update cache item */
    		mAlphabetNavItem[cacheIndex].letter = letter;
    		if(letter < 'a'-1 || letter > 'z') // 24?????
    		{
        		Log.i(TAG, " + letter failed: "+(char)letter);
    			mAlphabetNavItem[cacheIndex].letterBitmap = undefined;
    			mAlphabetNavItem[cacheIndex].letterBitmap.eraseColor(Color.argb(127, 122, 122, 0));
    		} 
    		else 
    		{
	    		if(!mAlbumNavItemUtils.fillAlphabetBitmap(
	    				mAlphabetNavItem[cacheIndex], 
	    				mBitmapWidth, 
	    				mBitmapHeight))
	    		{
	        		Log.i(TAG, " + letter failed to create bitmap: "+(char)letter);
	    			mAlphabetNavItem[cacheIndex].letterBitmap = undefined;
	    			mAlphabetNavItem[cacheIndex].letterBitmap.eraseColor(Color.argb(127, 122, 122, 0));
	
	    		}
//	    		if(Math.abs(mTargetPositionY - mPositionY) < 3 ||
//	    				mPositionY < 3){ // avoid unnecessary processing
//		    		if(!mAlbumNavItemUtils.fillAlbumLabel(
//		    				mAlbumNavItem[cacheIndex],
//		    				mBitmapWidth,
//		    				mBitmapHeight/4))
//		    		{
//	//	    			mAlbumNavItem[cacheIndex].label = undefined;
//	//	    			mAlbumNavItem[cacheIndex].label.eraseColor(Color.argb(0, 0, 0, 0));
//		    		}
//	    		}
    		}
    		
//    		Log.i(TAG, "cacheIndex: "+cacheIndex+"");
    		
	    	/** bind new texture */
    		bindTexture(gl, mAlphabetNavItem[cacheIndex].letterBitmap, mTextureAlphabetId[cacheIndex]);
    		
    		return true;
    	} else  {
    		return false;
    	}
    	
    }
    
    private void bindTexture(GL10 gl, Bitmap bitmap, int textureId){
    	/** bind new texture */
        gl.glBindTexture(GL10.GL_TEXTURE_2D, textureId);
    	
        gl.glTexParameterf(
        		GL10.GL_TEXTURE_2D, 
        		GL10.GL_TEXTURE_MIN_FILTER,
                GL10.GL_LINEAR);
        gl.glTexParameterf(
        		GL10.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_MAG_FILTER,
                GL10.GL_LINEAR);

        gl.glTexParameterf(
        		GL10.GL_TEXTURE_2D, 
        		GL10.GL_TEXTURE_WRAP_S,
                GL10.GL_CLAMP_TO_EDGE);
        gl.glTexParameterf(
        		GL10.GL_TEXTURE_2D, 
        		GL10.GL_TEXTURE_WRAP_T,
                GL10.GL_CLAMP_TO_EDGE);
        
//        gl.glTexParameterf(
//        		GL10.GL_TEXTURE_2D, 
//        		GL10.GL_TEXTURE_COLOR_BORDER,
//                GL10.GL_NICEST);

        gl.glTexEnvf(
        		GL10.GL_TEXTURE_ENV, 
        		GL10.GL_TEXTURE_ENV_MODE,
        		GL10.GL_MODULATE);
//        		GL10.GL_DECAL);
//        		GL10.GL_MODULATE);
//        		GL10.GL_BLEND);
//                GL10.GL_REPLACE);


        if(bitmap != null)
        	GLUtils.texImage2D(
        			GL10.GL_TEXTURE_2D, 
        			0, 
        			bitmap, 
        			0);

    }
    
    float getPositionX(){
    	return mPositionX;
    }

    float getTargetPositionX(){
    	return mTargetPositionX;
    }

    float getPositionY(){
    	return mPositionY;
    }

    float getTargetPositionY(){
    	return mTargetPositionY;
    }
    
    void saveRotationInitialPosition(){
    	mRotationInitialPositionY = mPositionY;
//    	mRotationInitialPositionX = mPositionX;
    }
    
    public void triggerPositionUpdate(){
    	updatePosition(true);
    	this.renderNow();
    }
    
    private boolean updatePosition(boolean force){
    	
//    	Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
    	
//    	updateFraction = 
//    		Math.min(
//    			(System.currentTimeMillis() - pTimestamp)/Constants.FRAME_DURATION_STD,
//    			Constants.FRAME_JUMP_MAX);
    	updateFraction = .5;
    	
//        Log.i(TAG, " + "+ (System.currentTimeMillis() - pTimestamp));

//    	Log.i(TAG, "framesPerSec: "+ 1000/(System.currentTimeMillis() - pTimestamp));
    	
    	/** 
    	 * New X pivot 
    	 */
    	if(mTargetPositionX > mPositionX)
			mPositionX +=
				Math.min(
					Math.min(
						Math.max(
								updateFraction * 
									Constants.SCROLL_SPEED_SMOOTHNESS * (mTargetPositionX-mPositionX), 
								updateFraction * 
									Constants.MIN_SCROLL)
						, mTargetPositionX-mPositionX)
					, Constants.MAX_SCROLL);
		else if(mTargetPositionX < mPositionX)
			mPositionX	 += 
				Math.max(
					Math.max(
						Math.min(
							updateFraction * 
								Constants.SCROLL_SPEED_SMOOTHNESS * (mTargetPositionX-mPositionX), 
							updateFraction * 
								-Constants.MIN_SCROLL)
						, mTargetPositionX-mPositionX)
					, -Constants.MAX_SCROLL);
		/**
		 * Finished scrolling X
		 */
		else if(mPositionX != 0 && mTargetPositionX % 1 == 0){
//			Log.i(TAG, "cleaning up after X rotation");
			
			mPositionY = findAlbumPositionAfterAlphabeticalScroll();
			mTargetPositionY = mPositionY;
			
			mPositionX = 0;
			mTargetPositionX = 0;
			
			lastInitial = -1;
			// need some more cleanup
		}
    	/**
    	 * Hmmm, double rotation -- strange -- FIXME
    	 */
    	if(mTargetPositionY != mPositionY &&
    		mTargetPositionX != 0)
    	{
    		lastInitial = -1;
    	}
		
//    	Log.i(TAG, "mTargetPosition: "+(mTargetPositionX % 1));
    	

		/** save state **/
		pTimestamp = System.currentTimeMillis();
	
    	/** optimization calculation*/
		flooredPositionX = (int) Math.floor(mPositionX);
		flooredPositionY = (int) Math.floor(mPositionY);
		
		positionOffsetX = mPositionX - flooredPositionX;
		positionOffsetY = mPositionY - flooredPositionY;
		

    	
   		/** 
   		 * New Y pivot
   		 */
    	// check if we are not outside our cursor
//    	if(mTargetPositionY >= mAlbumCursor.getCount() - 1)
//    		mTargetPositionY = mAlbumCursor.getCount() - 1;
//    	if(mPositionY >= mAlbumCursor.getCount() - 1)
//    		mPositionY = mAlbumCursor.getCount() - 1;
    	//		position += speedFactor * speed;
    	if(mTargetPositionY > mPositionY)
			mPositionY +=
				Math.min(
					Math.min(
						Math.max(
								updateFraction
									* Constants.SCROLL_SPEED_SMOOTHNESS * (mTargetPositionY-mPositionY), 
								updateFraction 
									* Constants.MIN_SCROLL)
						, mTargetPositionY-mPositionY)
					, Constants.MAX_SCROLL);
		else if(mTargetPositionY < mPositionY)
			mPositionY	 += 
				Math.max(
					Math.max(
						Math.min(
							updateFraction
								* Constants.SCROLL_SPEED_SMOOTHNESS * (mTargetPositionY-mPositionY), 
							updateFraction 
								* -Constants.MIN_SCROLL)
						, mTargetPositionY-mPositionY)
					, -Constants.MAX_SCROLL);

		/** are we outside the limits of the album list?*/
    	if(mAlbumCursor != null){
    		/** X checks */
    		if(lastInitial != -1 &&
    			lastInitial + mPositionX <= 'a' - 1 - Constants.MAX_POSITION_OVERSHOOT)
    		{
//    			Log.i(TAG, "lastInitial: "+(char)lastInitial+" mPositionX: "+mPositionX+" current: "+(char)(lastInitial + mPositionX));
	    		mTargetPositionX = 'a' - 1 - lastInitial;
    		}
	    	else if(lastInitial + mPositionX >= 'z' + Constants.MAX_POSITION_OVERSHOOT)
	    		mTargetPositionX = 'z' - lastInitial;
    		// TODO: are we done?
    		
    		/** Y checks */
    		if(mPositionY <= -Constants.MAX_POSITION_OVERSHOOT)
	    		mTargetPositionY = 0;
	    	else if(mPositionY >= mAlbumCursor.getCount() - 1 + Constants.MAX_POSITION_OVERSHOOT)
	    		mTargetPositionY = mAlbumCursor.getCount() - 1;
	    	
	    	/** are we done? */
	    	if(mTargetPositionY == (float)mPositionY){
	    		/* check limits */
	    		if(mPositionY < 0)
	    			mTargetPositionY = 0;
	    		else if(mPositionY > mAlbumCursor.getCount() - 1)
	    			mTargetPositionY = mAlbumCursor.getCount() - 1;
	    		/* yes, we are done scrolling */
	    		else if(!force)
	    			return false;
	    	}
    	}
    	
		
		return true;
    }
    
    /* optimization */
    char lastLetter;
    char newLetter;
    int	 letterIdx;
    /**
     * returns the index of the album cursor after alphabetical scroll
     * @return
     */
    private int findAlbumPositionAfterAlphabeticalScroll(){
    	if((int)mPositionY >= 0 && (int)mPositionY < mAlbumCursor.getCount())
    	{
	    	mAlbumCursor.moveToPosition((int)mPositionY);
	    	lastLetter = 
	    		mAlbumCursor.getString(
	    				mAlbumCursor.getColumnIndexOrThrow(
	    						MediaStore.Audio.Albums.ARTIST)).
	    				toLowerCase().charAt(0);
	    	newLetter = (char) (lastLetter + mPositionX);
	    	if(mPositionX > 0){
	    		for(letterIdx = (int)mPositionY; letterIdx<mAlbumCursor.getCount(); letterIdx++){
	    			mAlbumCursor.moveToPosition(letterIdx);
	    			if(mAlbumCursor.getString(
	    					mAlbumCursor.getColumnIndexOrThrow(
	    							MediaStore.Audio.Albums.ARTIST)).
	    					toLowerCase().charAt(0)
	    					>= newLetter)
	    			{
	    				break;
	    			}
	    		}
	    	} else {
	    		for(letterIdx = (int)mPositionY; letterIdx>=0; letterIdx--){
	    			mAlbumCursor.moveToPosition(letterIdx);
	    			if(mAlbumCursor.getString(
	    					mAlbumCursor.getColumnIndexOrThrow(
	    							MediaStore.Audio.Albums.ARTIST)).
	    					toLowerCase().charAt(0)
	    					<= newLetter)
	    			{
	    				break;
	    			}
	    		}
	    	}
	    	return letterIdx;
    	} else {
    		return 'a';
    	}
    }
    
    /** is the cube spinning */
    boolean isSpinning(){
    	// TODO: also check X scrolling
    	if(mTargetPositionY != mPositionY ||
    		mTargetPositionX != mPositionX)
    	{
    		return true;
    	} 
    	else 
    	{
    		return false;
    	}
    }
    
    /** is the cube spinning */
    boolean isSpinningX(){
    	// TODO: also check X scrolling
    	if(mTargetPositionX != mPositionX)
    	{
    		return true;
    	} 
    	else 
    	{
    		return false;
    	}
    }   
    
    /** is the cube spinning */
    boolean isSpinningY(){
    	// TODO: also check X scrolling
    	if(mTargetPositionY != mPositionY)
    	{
    		return true;
    	} 
    	else 
    	{
    		return false;
    	}
    }
    
    /** get album count */
    public int	getAlbumCount(){
    	if(mAlbumCursor == null)
    		return -1;
    	else
    		return mAlbumCursor.getCount();
    }
    
    /** get the current position */
    int	getShownPosition(float x, float y){
    	return (int) mPositionY;
    }
    
    /** get the current Album Id */
    int getShownAlbumId(float x, float y){
    	if(mTargetPositionY != mPositionY ||
    		mAlbumCursor == null ||
			/**
			 * FIXME: this is a quick cursor overflow bugfix, unverified
			 */
    		(int) mPositionY > mAlbumCursor.getCount() - 1)
    	{
//    		Log.i(TAG, "Target was not reached yet: "+mTargetPosition+" - "+mPosition);
    		return -1;
    	}
    	else{
    		int tmpIndex = mAlbumCursor.getPosition();
    		mAlbumCursor.moveToPosition((int) mPositionY);
    		int albumId = mAlbumCursor.getInt(
    				mAlbumCursor.getColumnIndexOrThrow(
    						MediaStore.Audio.Albums._ID));
    		mAlbumCursor.moveToPosition(tmpIndex);
    		return albumId;
    	}
    }
    
    /** get the current Album Name */
    String getShownAlbumName(float x, float y){
    	if(mTargetPositionY != mPositionY)
    		return null;
    	else{
    		int tmpIndex = mAlbumCursor.getPosition();
    		mAlbumCursor.moveToPosition((int) mPositionY);
    		String albumName = mAlbumCursor.getString(
    				mAlbumCursor.getColumnIndexOrThrow(
    						MediaStore.Audio.Albums.ALBUM));
    		mAlbumCursor.moveToPosition(tmpIndex);
    		return albumName;
    	}	
    }
    
    /** get the current Album Name */
    String getShownAlbumArtistName(float x, float y){
    	if(mTargetPositionY != mPositionY)
    		return null;
    	else{
    		int tmpIndex = mAlbumCursor.getPosition();
    		mAlbumCursor.moveToPosition((int) mPositionY);
    		String artistName = mAlbumCursor.getString(
    				mAlbumCursor.getColumnIndexOrThrow(
    						MediaStore.Audio.Albums.ARTIST));
    		mAlbumCursor.moveToPosition(tmpIndex);
    		return artistName;
    	}
    }
    
    /** move navigator to the specified album Id */
    int setCurrentByAlbumId(long albumId){

    	if(albumId >= 0)
    	{
	    	if(mAlbumCursor != null){
		    	for(int i = 0; i < mAlbumCursor.getCount()-1; i++){
		    		mAlbumCursor.moveToPosition(i);
		    		if(mAlbumCursor.getLong(mAlbumCursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)) == albumId){
		    			mTargetPositionY = i;
		    			mPositionY = 
		    				i - 
		    				Math.signum(mTargetPositionY - mPositionY)
		    				*
		    				Math.min(
		    					Math.abs(mTargetPositionY-mPositionY), 
		    					5.5f);
		    			// TODO: trigger rotation
		    			this.renderNow();
		    			return i;
		    		}
		    	}
		    	return -1;
	    	} else {
	    		return -1;
	    	}
    	} else {
    		return -1;
    	}
    }
    
    
    /**
     * Class members
     */
    private	int					mCacheSize = 4;
    private Context 			mContext;
    private Handler				mRequestRenderHandler;
    private RockOnCube 			mRockOnCube;
    private AlbumLabelGlText	mAlbumLabelGlText;
    private int[] 				mTextureId = new int[mCacheSize]; // the number of textures must be equal to the number of faces of our shape
    private int[] 				mTextureLabelId = new int[mCacheSize]; // the number of textures must be equal to the number of faces of our shape
    private int[] 				mTextureAlphabetId = new int[mCacheSize]; // the number of textures must be equal to the number of faces of our shape
    private	int					mScrollMode = Constants.SCROLL_MODE_VERTICAL;
    public	boolean				mClickAnimation = false;
    private	Cursor				mAlbumCursor = null;
    private AlphabetNavItem[]	mAlphabetNavItem = new AlphabetNavItem[mCacheSize];
    private AlbumNavItem[]		mAlbumNavItem = new AlbumNavItem[mCacheSize];
    private AlbumNavItemUtils	mAlbumNavItemUtils;
    private	int					mBitmapWidth;
    private int 				mBitmapHeight;
    private byte[]				mColorComponentBuffer;
    private	boolean				mForceTextureUpdate = false;
    private int					mHeight = 0;
    private int					mWidth = 0;

//    public	float		mPositionX = 0.f;
//    public	float		mTargetPositionX = 0.f;
//    public	float		mPositionY = 0.f;
//    public	float		mTargetPositionY = -1.f;
    private float		mEyeX = 0.75f;
    private float		mEyeY = -2.f;
    private float		mEyeZ = -5.f;
    private float		mEyeTargetX = 0.75f;
    private float		mEyeTargetY = 0.f;
    private float		mEyeTargetZ = -5.f;
    
    
    /** 
     * optimization vars 
     */
    private double 	pTimestamp;
    private int		flooredPositionX;
    private int		flooredPositionY;
    private float	positionOffsetX;
    private float	positionOffsetY;
    private float	rotationAngleX;
    private float	rotationAngleY;
    private boolean	texturesUpdated;
    private double	updateFraction;
    private int		tmpTextureIdx;
    

}





class RockOnCube {
	
	private final String TAG = "RockOnCube";
	
    public RockOnCube(int[] textureId, int[] textureAlphabetId) {

    	/** 
    	 * Save the texture ids
    	 */
    	mTextureId = textureId;
    	mTextureIdHorizontal = textureAlphabetId;
    	
    	/** 
    	 * Our cube coordinate declaration
    	 * we have 4 sets of 4 faces *only*
    	 * 
    	 * @ each instant only one of the sets
    	 *  is being drawn
    	 * when transitioning, 2 sets may be drawn
    	 */ 
    	ArrayList<float[]> faceCoordsArray = new ArrayList<float[]>(4);
//    	for(int i=0; i<mCubeFaces; i++){
//    		faceCoordsArray.add(new ArrayList<float[]>(mCubeFaces));
//    	}
    	
    	/**
    	 * 1ST FACE SET
    	 */
    	// FACE 0
        float[] coords0 = {
        		// X, Y, Z
        		-1.f, 1.f, -1.f,
        		1.f, 1.f, -1.f, 
        		1.f, -1.f, -1.f,
        		-1.f, -1.f, -1.f
        };
        faceCoordsArray.add(coords0);
        
        // FACE 1
        float[] coords1 = {
        		// X, Y, Z
        		-1.f, 1.f, 1.f,
        		1.f, 1.f, 1.f,
        		1.f, 1.f, -1.f,
        		-1.f, 1.f, -1.f
        };
        faceCoordsArray.add(coords1);
        
        // FACE 2
        float[] coords2 = {
                // X, Y, Z
        		-1.f, -1.f, 1.f,
        		1.f, -1.f, 1.f,
        		1.f, 1.f, 1.f,
        		-1.f, 1.f, 1.f
        };
        faceCoordsArray.add(coords2);
        
        // FACE 3
        float[] coords3 = {
        		// X, Y, Z
        		-1.f, -1.f, -1.f,
        		1.f, -1.f, -1.f,
        		1.f, -1.f, 1.f,
        		-1.f, -1.f, 1.f
        };
        faceCoordsArray.add(coords3);
        
//        /**
//    	 * 2ND FACE SET
//    	 */
//    	// FACE 0
//        float[] coords10 = {
//        		// X, Y, Z
//        		1.f, 1.f, -1.f,
//        		1.f, 1.f, 1.f, 
//        		1.f, -1.f, 1.f,
//        		1.f, -1.f, -1.f
//        };
//        faceCoordsArray.get(1).add(coords10);
//        
//        // FACE 1
//        float[] coords11 = {
//        		// X, Y, Z
//        		1.f, -1.f, -1.f,
//        		1.f, -1.f, 1.f,
//        		-1.f, -1.f, 1.f,
//        		-1.f, -1.f, -1.f
//        };
//        faceCoordsArray.get(1).add(coords11);
//        
//        // FACE 2
//        float[] coords12 = {
//                // X, Y, Z
//        		-1.f, -1.f, -1.f,
//        		-1.f, -1.f, 1.f,
//        		-1.f, 1.f, 1.f,
//        		-1.f, 1.f, -1.f
//        };
//        faceCoordsArray.get(1).add(coords12);
//        
//        // FACE 3
//        float[] coords13 = {
//        		// X, Y, Z
//        		-1.f, -1.f, -1.f,
//        		1.f, -1.f, -1.f,
//        		1.f, -1.f, 1.f,
//        		-1.f, -1.f, 1.f
//        };
//        faceCoordsArray.get(1).add(coords13);
//        
//        /**
//    	 * 3RD FACE SET
//    	 */
//    	// FACE 0
//        float[] coords20 = {
//        		// X, Y, Z
//        		1.f, 1.f, 1.f,
//        		-1.f, 1.f, 1.f, 
//        		-1.f, -1.f, 1.f,
//        		1.f, -1.f, 1.f
//        };
//        faceCoordsArray.get(2).add(coords20);
//        
//        // FACE 1
//        float[] coords21 = {
//        		// X, Y, Z
//        		1.f, -1.f, 1.f,
//        		-1.f, -1.f, 1.f,
//        		-1.f, -1.f, -1.f,
//        		1.f, -1.f, -1.f
//        };
//        faceCoordsArray.get(2).add(coords21);
//        
//        // FACE 2
//        float[] coords22 = {
//                // X, Y, Z
//        		1.f, -1.f, -1.f,
//        		-1.f, -1.f, -1.f,
//        		-1.f, 1.f, -1.f,
//        		1.f, 1.f, -1.f
//        };
//        faceCoordsArray.get(2).add(coords22);
//        
//        // FACE 3
//        float[] coords23 = {
//        		// X, Y, Z
//        		1.f, 1.f, -1.f,
//        		-1.f, 1.f, -1.f,
//        		-1.f, 1.f, 1.f,
//        		1.f, 1.f, 1.f
//        };
//        faceCoordsArray.get(2).add(coords23);
//        
//        /**
//    	 * 4TH FACE SET
//    	 */
//    	// FACE 0
//        float[] coords30 = {
//        		// X, Y, Z
//        		-1.f, 1.f, 1.f,
//        		-1.f, 1.f, -1.f, 
//        		-1.f, -1.f, -1.f,
//        		-1.f, -1.f, 1.f
//        };
//        faceCoordsArray.get(3).add(coords30);
//        
//        // FACE 1
//        float[] coords31 = {
//        		// X, Y, Z
//        		-1.f, -1.f, 1.f,
//        		-1.f, -1.f, -1.f,
//        		1.f, -1.f, -1.f,
//        		1.f, -1.f, 1.f
//        };
//        faceCoordsArray.get(3).add(coords31);
//        
//        // FACE 2
//        float[] coords32 = {
//                // X, Y, Z
//        		1.f, -1.f, 1.f,
//        		1.f, -1.f, -1.f,
//        		1.f, 1.f, -1.f,
//        		1.f, 1.f, 1.f
//        };
//        faceCoordsArray.get(3).add(coords32);
//        
//        // FACE 3
//        float[] coords33 = {
//        		// X, Y, Z
//        		1.f, 1.f, 1.f,
//        		1.f, 1.f, -1.f,
//        		-1.f, 1.f, -1.f,
//        		-1.f, 1.f, 1.f
//        };
//        faceCoordsArray.get(3).add(coords33);
        
        float[] coordsAlphabet = {
        		// 1ST SET OF 4
        		// 1 - X, Y, Z
        		-1,1,-1,
        		1,1,-1,
        		1,-1,-1,
        		-1,-1,-1,
        		// 2 - X, Y, Z
        		1,1,-1,
        		1,1,1,
        		1,-1,1,
        		1,-1,-1,
        		// 3 - X, Y, Z
        		1,1,1,
        		-1,1,1,
        		-1,-1,1,
        		1,-1,1,
        		// 4 - X, Y, Z
        		-1,1,1,
        		-1,1,-1,
        		-1,-1,-1,
        		-1,-1,1,
        		// 2ND SET OF 4
        		// 1 - X, Y, Z
        		-1,1,1,
        		1,1,1,
        		1,1,-1,
        		-1,1,-1,
        		// 2 - X, Y, Z
        		1,1,1,
        		1,-1,1,
        		1,-1,-1,
        		1,1,-1,
        		// 3 - X, Y, Z
        		1,-1,1,
        		-1,-1,1,
        		-1,-1,-1,
        		1,-1,-1,
        		// 4 - X, Y, Z
        		-1,-1,1,
        		-1,1,1,
        		-1,1,-1,
        		-1,-1,-1,
        		// 3RD SET OF 4
        		// 1 - X, Y, Z
        		-1,-1,1,
        		1,-1,1,
        		1,1,1,
        		-1,1,1,
        		// 2 - X, Y, Z
        		1,-1,1,
        		1,-1,-1,
        		1,1,-1,
        		1,1,1,
        		// 3 - X, Y, Z
        		1,-1,-1,
        		-1,-1,-1,
        		-1,1,-1,
        		1,1,-1,
        		// 4 - X, Y, Z
        		-1,-1,-1,
        		-1,-1,1,
        		-1,1,1,
        		-1,1,-1,
        		// 4TH SET OF 4
        		// 1 - X, Y, Z
        		-1,-1,-1,
        		1,-1,-1,
        		1,-1,1,
        		-1,-1,1,
        		// 2 - X, Y, Z
        		1,-1,-1,
        		1,1,-1,
        		1,1,1,
        		1,-1,1,
        		// 3 - X, Y, Z
        		1,1,-1,
        		-1,1,-1,
        		-1,1,1,
        		1,1,1,
        		// 4 - X, Y, Z
        		-1,1,-1,
        		-1,-1,-1,
        		-1,-1,1,
        		-1,1,1,
        };
        
        
        /**
         * Texture coordinates
         */
        float[] textCoords = {
	        		0.f, 1.f,
	        		1.f, 1.f,
	        		1.f, 0.f,
	        		0.f, 0.f
        };
        
    	/**
    	 * Generate our openGL buffers with the 
    	 * vertice and texture coordinates 
    	 * and drawing indexes
    	 * VERTICAL 
    	 */
//        for(int l = 0; l < mCubeFaces; l++){
    	for(int k = 0; k < mCubeFaces; k++){
	        // Buffers to be passed to gl*Pointer() functions
	        // must be direct, i.e., they must be placed on the
	        // native heap where the garbage collector cannot
	        // move them.
	        //
	        // Buffers with multi-byte datatypes (e.g., short, int, float)
	        // must have their byte order set to native order
	
	        ByteBuffer vbb = ByteBuffer.allocateDirect(VERTS * 3 * 4); // verts * ncoords * bytes per vert??
	        vbb.order(ByteOrder.nativeOrder());
	        mFVertexBuffer[k] = vbb.asFloatBuffer();
	
	        ByteBuffer tbb = ByteBuffer.allocateDirect(VERTS * 2 * 4);
	        tbb.order(ByteOrder.nativeOrder());
	        mTexBuffer[k] = tbb.asFloatBuffer();
	
	        ByteBuffer ibb = ByteBuffer.allocateDirect(VERTS * 2);
	        ibb.order(ByteOrder.nativeOrder());
	        mIndexBuffer[k] = ibb.asShortBuffer();
	
	        float[] coords = faceCoordsArray.get(k);
	        
	        for (int i = 0; i < VERTS; i++) {
	            for(int j = 0; j < 3; j++) {
	            	mFVertexBuffer[k].put(coords[i*3+j]);
	            }
	        }
	
	        
//	        for (int i = 0; i < VERTS; i++) {
//	            for(int j = 0; j < 2; j++) {
//	                mTexBuffer[k].put(coords[i*3+j]);
//	            }
//	        }
	        mTexBuffer[k].put(textCoords);
	        
	
	        for(int i = 0; i < VERTS; i++) {
	            mIndexBuffer[k].put((short) i);
	        }
	
	        mFVertexBuffer[k].position(0);
	        mTexBuffer[k].position(0);
	        mIndexBuffer[k].position(0);
    	}

    	   
    	/**
    	 * Generate our openGL buffers with the 
    	 * vertice and texture coordinates 
    	 * and drawing indexes
    	 * HORIZONTAL 
    	 */
        for(int l = 0; l < mCubeFaces; l++){
			for(int k = 0; k < mCubeFaces; k++){
		        // Buffers to be passed to gl*Pointer() functions
		        // must be direct, i.e., they must be placed on the
		        // native heap where the garbage collector cannot
		        // move them.
		        //
		        // Buffers with multi-byte datatypes (e.g., short, int, float)
		        // must have their byte order set to native order
		
		        ByteBuffer vbb = ByteBuffer.allocateDirect(VERTS * 3 * 4); // verts * ncoords * bytes per vert??
		        vbb.order(ByteOrder.nativeOrder());
		        mFVertexBufferHorizontal[l][k] = vbb.asFloatBuffer();
		
		        ByteBuffer tbb = ByteBuffer.allocateDirect(VERTS * 2 * 4);
		        tbb.order(ByteOrder.nativeOrder());
		        mTexBufferHorizontal[l][k] = tbb.asFloatBuffer();
		
		        ByteBuffer ibb = ByteBuffer.allocateDirect(VERTS * 2);
		        ibb.order(ByteOrder.nativeOrder());
		        mIndexBufferHorizontal[l][k] = ibb.asShortBuffer();
	
		        for (int i = 0; i < VERTS; i++) {
		            for(int j = 0; j < 3; j++) {
//		            	mFVertexBufferHorizontal[l][k].put(coords[i*3+j]);
		            	mFVertexBufferHorizontal[l][k].put(coordsAlphabet[l*VERTS*3*mCubeFaces+k*VERTS*3+i*3+j]);
		            }
		        }
		
		        
		//        for (int i = 0; i < VERTS; i++) {
		//            for(int j = 0; j < 2; j++) {
		//                mTexBuffer[k].put(coords[i*3+j]);
		//            }
		//        }
		        mTexBufferHorizontal[l][k].put(textCoords);
		        
		
		        for(int i = 0; i < VERTS; i++) {
		            mIndexBufferHorizontal[l][k].put((short) i);
		        }
		
		        mFVertexBufferHorizontal[l][k].position(0);
		        mTexBufferHorizontal[l][k].position(0);
		        mIndexBufferHorizontal[l][k].position(0);
			}
	    }
	}

    /* optimization */
//    int	x;
    int y;
    public void draw(GL10 gl) {
    	
    	/**
    	 * Vertical scrolling, only draw covers
    	 */
    	if(horizontalIndexFloat < 1 && horizontalIndexFloat > -1){
	    	for(int i = 0; i < mTextureId.length; i++){
	    		gl.glActiveTexture(GL10.GL_TEXTURE0);
		        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextureId[i]);
		        gl.glTexParameterx(
		        		GL10.GL_TEXTURE_2D, 
		        		GL10.GL_TEXTURE_WRAP_S,
		                GL10.GL_REPEAT);
		        gl.glTexParameterx(
		        		GL10.GL_TEXTURE_2D, 
		        		GL10.GL_TEXTURE_WRAP_T,
		                GL10.GL_REPEAT);
		    	
		        gl.glFrontFace(GL10.GL_CCW);
		        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer[i]);
		        gl.glEnable(GL10.GL_TEXTURE_2D);
		        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTexBuffer[i]);
		        gl.glDrawElements(
		//        		GL10.GL_TRIANGLE_STRIP,
		        		GL10.GL_TRIANGLE_FAN,
		//        		GL10.GL_LINE_LOOP,
		        		VERTS,
		                GL10.GL_UNSIGNED_SHORT,
		                mIndexBuffer[i]);
	    	}
    	}
    	/**
    	 * Horizontal scrolling - draw alphabetical indexes if needed
    	 */
    	y = verticalIndex % mCubeFaces;
    	if(y<0)
    		y=0;
    	if(horizontalIndexFloat != 0){ // should be a floating point otherwise we only get indexes after one face has rotated
    		for(int i = 0; i < 4; i++){
//	    		Log.i(TAG, "drawing alphabet "+i);
	    		gl.glActiveTexture(GL10.GL_TEXTURE0);
		        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextureIdHorizontal[i]);
		        gl.glTexParameterx(
		        		GL10.GL_TEXTURE_2D, 
		        		GL10.GL_TEXTURE_WRAP_S,
		                GL10.GL_REPEAT);
		        gl.glTexParameterx(
		        		GL10.GL_TEXTURE_2D, 
		        		GL10.GL_TEXTURE_WRAP_T,
		                GL10.GL_REPEAT);
		    	
		        gl.glFrontFace(GL10.GL_CCW);
		        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBufferHorizontal[y][i]);
		        gl.glEnable(GL10.GL_TEXTURE_2D);
		        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTexBufferHorizontal[y][i]);
		        gl.glDrawElements(
		//        		GL10.GL_TRIANGLE_STRIP,
		        		GL10.GL_TRIANGLE_FAN,
		//        		GL10.GL_LINE_LOOP,
		        		VERTS,
		                GL10.GL_UNSIGNED_SHORT,
		                mIndexBufferHorizontal[y][i]);
	    	}
//	    	}
    	}
    }

    public void setHorizontalIndex(int idx, float idxF){
//    	horizontalIndex = idx;
    	horizontalIndexFloat = idxF;
    }
    
    public void setVerticalIndex(int idx, float idxF){
    	verticalIndex = idx;
//    	verticalIndexFloat = idxF;
    }
    
    private final static int 	VERTS = 4;
	private final int 			mCubeFaces = 4;
	private final int 			pointsPerFace = 4;
//	private int					horizontalIndex = 0;
	private float				horizontalIndexFloat = 0;
	private int					verticalIndex = 0;
//	private float				verticalIndexFloat = 0;
    
	/* vertical scrolling buffers */
	private FloatBuffer mFVertexBuffer[] = new FloatBuffer[mCubeFaces];
    private FloatBuffer mTexBuffer[] = new FloatBuffer[mCubeFaces];
    private ShortBuffer mIndexBuffer[] = new ShortBuffer[mCubeFaces];
    
    /* horizontal scrolling buffers */
    private FloatBuffer mFVertexBufferHorizontal[][] = new FloatBuffer[mCubeFaces][mCubeFaces];
    private FloatBuffer mTexBufferHorizontal[][] = new FloatBuffer[mCubeFaces][mCubeFaces];
    private ShortBuffer mIndexBufferHorizontal[][] = new ShortBuffer[mCubeFaces][mCubeFaces];
    
    /** our 4 vertical face textures */
    public	int[] mTextureId;
    
    /** our 4 horizontal face textures */
    public	int[] mTextureIdHorizontal;
    
}

class AlbumLabelGlText{
	
	public void setTexture(int textureId){
		mTextureId = textureId;
	}
	
    public AlbumLabelGlText(int textureId) {

    	/** 
    	 * Save the texture ids
    	 */
    	mTextureId = textureId;
    	
    	/** 
    	 * Our cube coordinate declaration
    	 */ 
    	ArrayList<float[]> faceCoordsArray = new ArrayList<float[]>(mCubeFaces);
    	
    	// FACE 0
        float[] coords0 = {
        		// X, Y, Z
        		-1.f, .25f, 0.f,
        		1.f, .25f, 0.f,
        		1.f, -.25f, 0.f, 
        		-1.f, -.25f, 0.f
        };
        faceCoordsArray.add(coords0);
        
//        // FACE 1
//        float[] coords1 = {
//        		// X, Y, Z
//        		-1.f, 1.1f, -.5f,
//        		1.f, 1.1f, -5f,
//        		1.f, 1.1f, -1.f,
//        		-1.f, 1.1f, -1.f
//        };
//        faceCoordsArray.add(coords1);
//        
//        // FACE 2
//        float[] coords2 = {
//                // X, Y, Z
//        		-1.f, .5f, 1.1f,
//        		1.f, .5f, 1.1f,
//        		1.f, 1.f, 1.1f,
//        		-1.f, 1.f, 1.1f
//        };
//        faceCoordsArray.add(coords2);
//        
//        // FACE 3
//        float[] coords3 = {
//        		// X, Y, Z
//        		-1.f, -1.1f, .5f,
//        		1.f, -1.1f, .5f,
//         		1.f, -1.1f, 1.f,
//        		-1.f, -1.1f, 1.f
//        };
//        faceCoordsArray.add(coords3);
//        
        // General texture coords
        float[] textCoords = {
	        		0.f, 1.f,
	        		1.f, 1.f,
	        		1.f, 0.f,
	        		0.f, 0.f
        };
        
//    	/**
//    	 * Generate our openGL buffers with the 
//    	 * vertice and texture coordinates 
//    	 * and drawing indexes
//    	 */
//    	for(int k = 0; k < mCubeFaces; k++){
//	        // Buffers to be passed to gl*Pointer() functions
//	        // must be direct, i.e., they must be placed on the
//	        // native heap where the garbage collector cannot
//	        // move them.
//	        //
//	        // Buffers with multi-byte datatypes (e.g., short, int, float)
//	        // must have their byte order set to native order
	
	        ByteBuffer vbb = ByteBuffer.allocateDirect(VERTS * 3 * 4); // verts * ncoords * bytes per vert??
	        vbb.order(ByteOrder.nativeOrder());
	        mFVertexBuffer = vbb.asFloatBuffer();
	
	        ByteBuffer tbb = ByteBuffer.allocateDirect(VERTS * 2 * 4);
	        tbb.order(ByteOrder.nativeOrder());
	        mTexBuffer = tbb.asFloatBuffer();
	
	        ByteBuffer ibb = ByteBuffer.allocateDirect(VERTS * 2);
	        ibb.order(ByteOrder.nativeOrder());
	        mIndexBuffer = ibb.asShortBuffer();
	
	        float[] coords = faceCoordsArray.get(0);
	        
	        for (int i = 0; i < VERTS; i++) {
	            for(int j = 0; j < 3; j++) {
	            	mFVertexBuffer.put(coords[i*3+j]);
	            }
	        }
	
	        
//	        for (int i = 0; i < VERTS; i++) {
//	            for(int j = 0; j < 2; j++) {
//	                mTexBuffer[k].put(coords[i*3+j]);
//	            }
//	        }
	        mTexBuffer.put(textCoords);
	        
	
	        for(int i = 0; i < VERTS; i++) {
	            mIndexBuffer.put((short) i);
	        }
	
	        mFVertexBuffer.position(0);
	        mTexBuffer.position(0);
	        mIndexBuffer.position(0);
//    	}
    }

    public void draw(GL10 gl) {
    	
//    	for(int i = 0; i < mTextureId.length; i++){
    		gl.glActiveTexture(GL10.GL_TEXTURE0);
	        gl.glBindTexture(GL10.GL_TEXTURE_2D, mTextureId);
	        gl.glTexParameterx(
	        		GL10.GL_TEXTURE_2D, 
	        		GL10.GL_TEXTURE_WRAP_S,
	                GL10.GL_REPEAT);
	        gl.glTexParameterx(
	        		GL10.GL_TEXTURE_2D, 
	        		GL10.GL_TEXTURE_WRAP_T,
	                GL10.GL_REPEAT);
	    	
	        gl.glFrontFace(GL10.GL_CCW);
	        gl.glVertexPointer(3, GL10.GL_FLOAT, 0, mFVertexBuffer);
	        gl.glEnable(GL10.GL_TEXTURE_2D);
	        gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, mTexBuffer);
	        gl.glDrawElements(
	//        		GL10.GL_TRIANGLE_STRIP,
	        		GL10.GL_TRIANGLE_FAN,
	//        		GL10.GL_LINE_LOOP,
	        		VERTS,
	                GL10.GL_UNSIGNED_SHORT,
	                mIndexBuffer);
//    	}
    }

    private final static int VERTS = 4;
	private final int mCubeFaces = 4;
	private final int pointsPerFace = 4;
    
    private FloatBuffer mFVertexBuffer;
    private FloatBuffer mTexBuffer;
    private ShortBuffer mIndexBuffer;
    
    /** our texture id */
    public	int mTextureId;
}