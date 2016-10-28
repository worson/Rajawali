/**
 * Copyright 2013 Dennis Ippel
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.rajawali3d.textures;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;

import org.rajawali3d.textures.annotation.Filter;
import org.rajawali3d.textures.annotation.Filter.FilterType;
import org.rajawali3d.textures.annotation.Type.TextureType;
import org.rajawali3d.textures.annotation.Wrap;
import org.rajawali3d.textures.annotation.Wrap.WrapType;

/**
 * This class is used to specify common functions of a single texture.
 *
 * @author dennis.ippel
 * @author Jared Woolston (Jared.Woolston@gmail.com)
 */
@SuppressWarnings("WeakerAccess")
public abstract class SingleTexture extends BaseTexture {

    /**
     * The texture data.
     */
    private TextureDataReference textureData;

    /**
     * The Android resource ID of the data for this texture, if it was provided. Otherwise, 0.
     */
    protected int resourceId;

    /**
     * Constructs a new {@link SingleTexture} with the specified name and type.
     *
     * @param type {@link TextureType} The texture usage type.
     * @param name {@link String} The texture name.
     */
    public SingleTexture(@TextureType int type, @NonNull String name) {
        super(type, name);
    }

    /**
     * Constructs a new {@link SingleTexture} with data provided by the Android resource id. The texture name is set by
     * querying Android for the resource name.
     *
     * @param context {@link Context} The application context.
     * @param type {@link TextureType} The texture usage type.
     * @param resourceId {@code int} The Android resource id to load from.
     */
    public SingleTexture(@NonNull Context context, @TextureType int type, @DrawableRes int resourceId) {
        this(type, context.getResources().getResourceName(resourceId));
        setResourceId(context, resourceId);
    }

    /**
     * Constructs a new {@link SingleTexture} with the provided data.
     *
     * @param type {@link TextureType} The texture usage type.
     * @param name {@link String} The texture name.
     * @param data {@link TextureDataReference} The texture data.
     */
    public SingleTexture(@TextureType int type, @NonNull String name, @NonNull TextureDataReference data) {
        this(type, name);
        setTextureData(data);
    }

    /**
     * Constructs a new {@link SingleTexture} with data provided by a {@link CompressedTexture}.
     *
     * @param type {@link TextureType} The texture usage type.
     * @param name {@link String} The texture name.
     * @param compressedTexture {@link CompressedTexture} The compressed texture data.
     */
    public SingleTexture(@TextureType int type, @NonNull String name, @NonNull CompressedTexture compressedTexture) {
        super(type, name, compressedTexture);
    }

    /**
     * Constructs a new {@link SingleTexture} with data and settings from the provided {@link SingleTexture}.
     *
     * @param other The other {@link SingleTexture}.
     */
    public SingleTexture(@NonNull SingleTexture other) {
        super(other);
        setFrom(other);
    }

    /**
     * Creates a clone
     */
    public abstract SingleTexture clone();

    /**
     * Copies all properties and data from another {@link SingleTexture}.
     *
     * @param other The other {@link SingleTexture}.
     */
    public void setFrom(@NonNull SingleTexture other) {
        super.setFrom(other);
        setTextureData(other.getTextureData());
    }

    /**
     * Sets the resource id used by this {@link SingleTexture} for a data source. This will create a new
     * {@link TextureDataReference}.
     *
     * @param context {@link Context}
     * @param resourceId
     * @return
     */
    @NonNull
    public TextureDataReference setResourceId(@NonNull Context context, @DrawableRes int resourceId) {
        this.resourceId = resourceId;

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;

        final Bitmap bitmap = BitmapFactory.decodeResource(context.getResources(), resourceId, options);
        setTextureData(new TextureDataReference(bitmap, null, bitmap.getConfig().equals(Config.RGB_565)
            ? GLES20.GL_RGB : GLES20.GL_RGBA,
            GLES20.GL_UNSIGNED_BYTE));
        return textureData;
    }

    public int getResourceId() {
        return resourceId;
    }

    public void setTextureData(@NonNull TextureDataReference data) {
        if (textureData != null) {
            textureData.recycle();
        }
        textureData = data;
        textureData.holdReference();
    }

    @NonNull
    public TextureDataReference getTextureData() {
        return textureData;
    }

    void add() throws TextureException {
        if (compressedTexture != null) {
            compressedTexture.add();
            setWidth(compressedTexture.getWidth());
            setHeight(compressedTexture.getHeight());
            setTextureId(compressedTexture.getTextureId());
            return;
        }

        if (textureData == null || textureData.isDestroyed() || (textureData.hasBuffer()
            && textureData.getByteBuffer().limit() == 0)) {
            throw new TextureException("Texture could not be added because there is no valid data set.");
        }

        if (textureData.hasBitmap()) {
            setTexelFormat(textureData.getBitmap().getConfig() == Config.ARGB_8888 ? GLES20.GL_RGBA : GLES20.GL_RGB);
            setWidth(textureData.getBitmap().getWidth());
            setHeight(textureData.getBitmap().getHeight());
        }

        int[] genTextureNames = new int[1];
        GLES20.glGenTextures(1, genTextureNames, 0);
        int textureId = genTextureNames[0];

        if (textureId > 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

            if (isMipmaped()) {
                if (filterType == Filter.LINEAR) {
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                        GLES20.GL_LINEAR_MIPMAP_LINEAR);
                } else {
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                        GLES20.GL_NEAREST_MIPMAP_NEAREST);
                }
            } else {
                if (filterType == Filter.LINEAR) {
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                } else {
                    GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                }
            }

            if (filterType == Filter.LINEAR) {
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            } else {
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            }

            if (wrapType == (Wrap.REPEAT_S | Wrap.REPEAT_T | Wrap.REPEAT_R)) {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            } else {
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            }

            if (textureData.hasBuffer()) {
                if (width == 0 || height == 0 || texelFormat == 0) {
                    throw new TextureException(
                        "Could not create ByteBuffer texture. One or more of the following properties haven't "
                            + "been set: width, height or bitmap format");
                }
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, texelFormat, width, height, 0, texelFormat,
                    GLES20.GL_UNSIGNED_BYTE, textureData.getByteBuffer());
            } else {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, texelFormat, textureData.getBitmap(), 0);
            }

            if (isMipmaped()) {
                GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            }

            setTextureId(textureId);
        } else {
            throw new TextureException("Couldn't generate a texture name.");
        }

        if (shouldRecycle) {
            textureData.recycle();
            textureData = null;
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    void remove() throws TextureException {
        if (compressedTexture != null) {
            compressedTexture.remove();
        } else {
            GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
        }
        if (textureData != null) {
            // When removing a texture, release a reference count for its data if we have saved it.
            textureData.recycle();
        }

        //TODO: Notify materials that were using this texture
    }

    void replace() throws TextureException {
        if (compressedTexture != null) {
            compressedTexture.replace();
            setWidth(compressedTexture.getWidth());
            setHeight(compressedTexture.getHeight());
            setTextureId(compressedTexture.getTextureId());
            return;
        }

        if (textureData == null || textureData.isDestroyed() || (textureData.hasBuffer()
            && textureData.getByteBuffer().limit() == 0)) {
            throw new TextureException("Texture could not be replaced because there is no Bitmap or ByteBuffer set.");
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        if (textureData.hasBitmap()) {
            int bitmapFormat = textureData.getBitmap().getConfig() == Config.ARGB_8888 ? GLES20.GL_RGBA : GLES20.GL_RGB;
            if (textureData.getBitmap().getWidth() != width || textureData.getBitmap().getHeight() != height) {
                throw new TextureException(
                    "Texture could not be updated because the texture size is different from the original.");
            }
            if (bitmapFormat != this.texelFormat) {
                throw new TextureException(
                    "Texture could not be updated because the bitmap format is different from the original");
            }

            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, textureData.getBitmap(), this.texelFormat,
                GLES20.GL_UNSIGNED_BYTE);
        } else if (textureData.hasBuffer()) {
            if (width == 0 || height == 0 || texelFormat == 0) {
                throw new TextureException(
                    "Could not update ByteBuffer texture. One or more of the following properties haven't been "
                        + "set: width, height or bitmap format");
            }
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width, height, texelFormat,
                GLES20.GL_UNSIGNED_BYTE, textureData.getByteBuffer());
        }

        if (mipmaped) {
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    void reset() throws TextureException {
        if (compressedTexture != null) {
            compressedTexture.reset();
            return;
        }

        if (textureData != null) {
            textureData.recycle();
            textureData = null;
        }
    }

    @Override
    public void setWrapType(@WrapType int wrapType) {
        super.setWrapType(wrapType);
        if (compressedTexture != null) {
            compressedTexture.setWrapType(wrapType);
        }
    }

    @Override
    public void setFilterType(@FilterType int filterType) {
        super.setFilterType(filterType);
        if (compressedTexture != null) {
            compressedTexture.setFilterType(filterType);
        }
    }
}