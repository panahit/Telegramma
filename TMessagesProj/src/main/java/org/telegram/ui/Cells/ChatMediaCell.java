/*
 * This is the source code of Telegram for Android v. 1.4.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.android.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.android.MessageObject;
import org.telegram.android.PhotoObject;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Views.GifDrawable;
import org.telegram.android.ImageReceiver;
import org.telegram.ui.Views.RoundProgressView;

import java.io.File;
import java.util.Locale;

public class ChatMediaCell extends ChatBaseCell implements MediaController.FileDownloadProgressListener {

    public static interface ChatMediaCellDelegate {
        public abstract void didPressedImage(ChatMediaCell cell);
    }

    private static Drawable placeholderInDrawable;
    private static Drawable placeholderOutDrawable;
    private static Drawable videoIconDrawable;
    private static Drawable[] buttonStatesDrawables = new Drawable[4];
    private static TextPaint infoPaint;
    private static MessageObject lastDownloadedGifMessage = null;

    private GifDrawable gifDrawable = null;

    private int photoWidth;
    private int photoHeight;
    private PhotoObject currentPhotoObject;
    private String currentUrl;
    private String currentPhotoFilter;
    private ImageReceiver photoImage;
    private RoundProgressView progressView;
    private boolean progressVisible = false;
    private boolean photoNotSet = false;
    private boolean cancelLoading = false;

    private int TAG;

    private int buttonState = 0;
    private int buttonPressed = 0;
    private boolean imagePressed = false;
    private int buttonX;
    private int buttonY;

    private StaticLayout infoLayout;
    private int infoWidth;
    private int infoOffset = 0;
    private String currentInfoString;

    public ChatMediaCellDelegate mediaDelegate = null;

    public ChatMediaCell(Context context) {
        super(context, true);

        if (placeholderInDrawable == null) {
            placeholderInDrawable = getResources().getDrawable(R.drawable.photo_placeholder_in);
            placeholderOutDrawable = getResources().getDrawable(R.drawable.photo_placeholder_out);
            buttonStatesDrawables[0] = getResources().getDrawable(R.drawable.photoload);
            buttonStatesDrawables[1] = getResources().getDrawable(R.drawable.photocancel);
            buttonStatesDrawables[2] = getResources().getDrawable(R.drawable.photogif);
            buttonStatesDrawables[3] = getResources().getDrawable(R.drawable.playvideo);
            videoIconDrawable = getResources().getDrawable(R.drawable.ic_video);

            infoPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            infoPaint.setColor(0xffffffff);
            infoPaint.setTextSize(AndroidUtilities.dp(12));
        }

        TAG = MediaController.getInstance().generateObserverTag();

        photoImage = new ImageReceiver(this);
        progressView = new RoundProgressView();
    }

    public void clearGifImage() {
        if (currentMessageObject != null && currentMessageObject.type == 8) {
            gifDrawable = null;
            buttonState = 2;
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (photoImage != null) {
            photoImage.clearImage();
            currentPhotoObject = null;
        }
        currentUrl = null;
        if (gifDrawable != null) {
            MediaController.getInstance().clearGifDrawable(this);
            gifDrawable = null;
        }
        MediaController.getInstance().removeLoadingFileObserver(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        boolean result = false;
        int side = AndroidUtilities.dp(44);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate == null || delegate.canPerformActions()) {
                if (buttonState != -1 && x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side) {
                    buttonPressed = 1;
                    invalidate();
                    result = true;
                } else if (photoImage.isInsideImage(x, y)) {
                    imagePressed = true;
                    result = true;
                }
                if (result) {
                    startCheckLongPress();
                }
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (buttonPressed == 1) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = 0;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedButton();
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    buttonPressed = 0;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= buttonX && x <= buttonX + side && y >= buttonY && y <= buttonY + side)) {
                        buttonPressed = 0;
                        invalidate();
                    }
                }
            } else if (imagePressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    imagePressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    didPressedImage();
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    imagePressed = false;
                    invalidate();
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!photoImage.isInsideImage(x, y)) {
                        imagePressed = false;
                        invalidate();
                    }
                }
            }
        }
        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    private void didPressedImage() {
        if (currentMessageObject.type == 1) {
            if (buttonState == -1) {
                if (mediaDelegate != null) {
                    mediaDelegate.didPressedImage(this);
                }
            } else if (buttonState == 0) {
                didPressedButton();
            }
        } else if (currentMessageObject.type == 8) {
            if (buttonState == -1) {
                buttonState = 2;
                if (gifDrawable != null) {
                    gifDrawable.pause();
                }
                invalidate();
            } else if (buttonState == 2 || buttonState == 0) {
                didPressedButton();
            }
        } else if (currentMessageObject.type == 3) {
            if (buttonState == 0 || buttonState == 3) {
                didPressedButton();
            }
        } else if (currentMessageObject.type == 4) {
            if (mediaDelegate != null) {
                mediaDelegate.didPressedImage(this);
            }
        }
    }

    private void didPressedButton() {
        if (buttonState == 0) {
            cancelLoading = false;
            if (currentMessageObject.type == 1) {
                if (currentMessageObject.imagePreview != null) {
                    photoImage.setImage(currentPhotoObject.photoOwner.location, currentPhotoFilter, new BitmapDrawable(currentMessageObject.imagePreview), currentPhotoObject.photoOwner.size);
                } else {
                    photoImage.setImage(currentPhotoObject.photoOwner.location, currentPhotoFilter, currentMessageObject.isOut() ? placeholderOutDrawable : placeholderInDrawable, currentPhotoObject.photoOwner.size);
                }
            } else if (currentMessageObject.type == 8) {
                FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.document);
                lastDownloadedGifMessage = currentMessageObject;
            } else if (currentMessageObject.type == 3) {
                FileLoader.getInstance().loadFile(currentMessageObject.messageOwner.media.video);
            }
            progressVisible = true;
            buttonState = 1;
            invalidate();
        } else if (buttonState == 1) {
            if (currentMessageObject.isOut() && currentMessageObject.messageOwner.send_state == MessageObject.MESSAGE_SEND_STATE_SENDING) {
                if (delegate != null) {
                    delegate.didPressedCancelSendButton(this);
                }
            } else {
                cancelLoading = true;
                if (currentMessageObject.type == 1) {
                    ImageLoader.getInstance().cancelLoadingForImageView(photoImage);
                } else if (currentMessageObject.type == 8) {
                    FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.document);
                    if (lastDownloadedGifMessage != null && lastDownloadedGifMessage.messageOwner.id == currentMessageObject.messageOwner.id) {
                        lastDownloadedGifMessage = null;
                    }
                } else if (currentMessageObject.type == 3) {
                    FileLoader.getInstance().cancelLoadFile(currentMessageObject.messageOwner.media.video);
                }
                progressVisible = false;
                buttonState = 0;
                invalidate();
            }
        } else if (buttonState == 2) {
            if (gifDrawable == null) {
                gifDrawable = MediaController.getInstance().getGifDrawable(this, true);
            }
            if (gifDrawable != null) {
                gifDrawable.start();
                gifDrawable.invalidateSelf();
                buttonState = -1;
                invalidate();
            }
        } else if (buttonState == 3) {
            if (mediaDelegate != null) {
                mediaDelegate.didPressedImage(this);
            }
        }
    }

    private boolean isPhotoDataChanged(MessageObject object) {
        if (object.type == 4) {
            if (currentUrl == null) {
                return true;
            }
            double lat = object.messageOwner.media.geo.lat;
            double lon = object.messageOwner.media.geo._long;
            String url = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=13&size=100x100&maptype=roadmap&scale=%d&markers=color:red|size:big|%f,%f&sensor=false", lat, lon, Math.min(2, (int)Math.ceil(AndroidUtilities.density)), lat, lon);
            if (!url.equals(currentUrl)) {
                return true;
            }
        } else if (currentPhotoObject == null) {
            return true;
        } else if (currentPhotoObject != null && photoNotSet) {
            String fileName = FileLoader.getAttachFileName(currentPhotoObject.photoOwner);
            File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName);
            if (cacheFile.exists()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject != messageObject || isPhotoDataChanged(messageObject) || isUserDataChanged()) {
            super.setMessageObject(messageObject);
            cancelLoading = false;

            progressVisible = false;
            buttonState = -1;
            gifDrawable = null;
            currentPhotoObject = null;
            currentUrl = null;
            photoNotSet = false;

            if (messageObject.type == 8) {
                gifDrawable = MediaController.getInstance().getGifDrawable(this, false);

                String str = Utilities.formatFileSize(messageObject.messageOwner.media.document.size);
                if (currentInfoString == null || !currentInfoString.equals(str)) {
                    currentInfoString = str;
                    infoOffset = 0;
                    infoWidth = (int) Math.ceil(infoPaint.measureText(currentInfoString));
                    infoLayout = new StaticLayout(currentInfoString, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
            } else if (messageObject.type == 3) {
                int duration = messageObject.messageOwner.media.video.duration;
                int minutes = duration / 60;
                int seconds = duration - minutes * 60;
                String str = String.format("%d:%02d, %s", minutes, seconds, Utilities.formatFileSize(messageObject.messageOwner.media.video.size));
                if (currentInfoString == null || !currentInfoString.equals(str)) {
                    currentInfoString = str;
                    infoOffset = videoIconDrawable.getIntrinsicWidth() + AndroidUtilities.dp(4);
                    infoWidth = (int) Math.ceil(infoPaint.measureText(currentInfoString));
                    infoLayout = new StaticLayout(currentInfoString, infoPaint, infoWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                }
            } else {
                currentInfoString = null;
                infoLayout = null;
            }

            if (messageObject.type == 4) {
                photoWidth = AndroidUtilities.dp(100);
                photoHeight = AndroidUtilities.dp(100);
                backgroundWidth = photoWidth + AndroidUtilities.dp(12);

                double lat = messageObject.messageOwner.media.geo.lat;
                double lon = messageObject.messageOwner.media.geo._long;
                currentUrl = String.format(Locale.US, "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=13&size=100x100&maptype=roadmap&scale=%d&markers=color:red|size:big|%f,%f&sensor=false", lat, lon, Math.min(2, (int)Math.ceil(AndroidUtilities.density)), lat, lon);
                photoImage.setImage(currentUrl, null, messageObject.isOut() ? placeholderOutDrawable : placeholderInDrawable);
            } else {
                photoWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
                photoHeight = photoWidth + AndroidUtilities.dp(100);

                if (photoWidth > 800) {
                    photoWidth = 800;
                }
                if (photoHeight > 800) {
                    photoHeight = 800;
                }

                currentPhotoObject = PhotoObject.getClosestImageWithSize(messageObject.photoThumbs, 800, 800);
                if (currentPhotoObject != null) {
                    float scale = (float) currentPhotoObject.photoOwner.w / (float) photoWidth;

                    int w = (int) (currentPhotoObject.photoOwner.w / scale);
                    int h = (int) (currentPhotoObject.photoOwner.h / scale);
                    if (w == 0) {
                        if (messageObject.type == 3) {
                            w = infoWidth + infoOffset + AndroidUtilities.dp(16);
                        } else {
                            w = AndroidUtilities.dp(100);
                        }
                    }
                    if (h == 0) {
                        h = AndroidUtilities.dp(100);
                    }
                    if (h > photoHeight) {
                        float scale2 = h;
                        h = photoHeight;
                        scale2 /= h;
                        w = (int) (w / scale2);
                    } else if (h < AndroidUtilities.dp(120)) {
                        h = AndroidUtilities.dp(120);
                        float hScale = (float) currentPhotoObject.photoOwner.h / h;
                        if (currentPhotoObject.photoOwner.w / hScale < photoWidth) {
                            w = (int) (currentPhotoObject.photoOwner.w / hScale);
                        }
                    }
                    int timeWidthTotal = timeWidth + AndroidUtilities.dp(14 + (currentMessageObject.isOut() ? 20 : 0));
                    if (w < timeWidthTotal) {
                        w = timeWidthTotal;
                    }

                    photoWidth = w;
                    photoHeight = h;
                    backgroundWidth = w + AndroidUtilities.dp(12);
                    currentPhotoFilter = String.format(Locale.US, "%d_%d", (int) (w / AndroidUtilities.density), (int) (h / AndroidUtilities.density));
                    if (messageObject.photoThumbs.size() > 1 || messageObject.type == 3 || messageObject.type == 8) {
                        currentPhotoFilter += "_b";
                    }

                    if (currentPhotoObject.image != null) {
                        photoImage.setImageBitmap(currentPhotoObject.image);
                    } else {
                        boolean photoExist = true;
                        String fileName = FileLoader.getAttachFileName(currentPhotoObject.photoOwner);
                        if (messageObject.type == 1) {
                            File cacheFile = new File(AndroidUtilities.getCacheDir(), fileName);
                            if (!cacheFile.exists()) {
                                photoExist = false;
                            } else {
                                MediaController.getInstance().removeLoadingFileObserver(this);
                            }
                        }
                        if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO)) {
                            if (messageObject.imagePreview != null) {
                                photoImage.setImage(currentPhotoObject.photoOwner.location, currentPhotoFilter, new BitmapDrawable(messageObject.imagePreview), currentPhotoObject.photoOwner.size);
                            } else {
                                photoImage.setImage(currentPhotoObject.photoOwner.location, currentPhotoFilter, messageObject.isOut() ? placeholderOutDrawable : placeholderInDrawable, currentPhotoObject.photoOwner.size);
                            }
                        } else {
                            photoNotSet = true;
                            if (messageObject.imagePreview != null) {
                                photoImage.setImageBitmap(messageObject.imagePreview);
                            } else {
                                photoImage.setImageBitmap(messageObject.isOut() ? placeholderOutDrawable : placeholderInDrawable);
                            }
                        }
                    }
                } else {
                    photoImage.setImageBitmap(messageObject.isOut() ? placeholderOutDrawable : placeholderInDrawable);
                }
            }

            invalidate();
        }
        updateButtonState();
    }

    public ImageReceiver getPhotoImage() {
        return photoImage;
    }

    public void updateButtonState() {
        String fileName = null;
        File cacheFile = null;
        if (currentMessageObject.type == 1) {
            if (currentPhotoObject == null) {
                return;
            }
            fileName = FileLoader.getAttachFileName(currentPhotoObject.photoOwner);
            cacheFile = new File(AndroidUtilities.getCacheDir(), fileName);
        } else if (currentMessageObject.type == 8 || currentMessageObject.type == 3) {
            if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() != 0) {
                File f = new File(currentMessageObject.messageOwner.attachPath);
                if (f.exists()) {
                    fileName = currentMessageObject.messageOwner.attachPath;
                    cacheFile = f;
                }
            }
            if (fileName == null) {
                fileName = currentMessageObject.getFileName();
                cacheFile = new File(AndroidUtilities.getCacheDir(), fileName);
            }
        }
        if (fileName == null) {
            return;
        }
        if (currentMessageObject.isOut() && currentMessageObject.messageOwner.send_state == MessageObject.MESSAGE_SEND_STATE_SENDING) {
            if (currentMessageObject.messageOwner.attachPath != null) {
                MediaController.getInstance().addLoadingFileObserver(currentMessageObject.messageOwner.attachPath, this);
                progressVisible = true;
                buttonState = 1;
                Float progress = FileLoader.getInstance().getFileProgress(currentMessageObject.messageOwner.attachPath);
                if (progress != null) {
                    progressView.setProgress(progress);
                } else {
                    progressView.setProgress(0);
                }
                invalidate();
            }
        } else {
            if (currentMessageObject.messageOwner.attachPath != null && currentMessageObject.messageOwner.attachPath.length() != 0) {
                MediaController.getInstance().removeLoadingFileObserver(this);
            }
            if (cacheFile.exists() && cacheFile.length() == 0) {
                cacheFile.delete();
            }
            if (!cacheFile.exists()) {
                MediaController.getInstance().addLoadingFileObserver(fileName, this);
                if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                    if (cancelLoading || currentMessageObject.type != 1 || !MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO)) {
                        buttonState = 0;
                        progressVisible = false;
                    } else {
                        buttonState = 1;
                        progressVisible = true;
                    }
                    progressView.setProgress(0);
                } else {
                    buttonState = 1;
                    progressVisible = true;
                    Float progress = FileLoader.getInstance().getFileProgress(fileName);
                    if (progress != null) {
                        progressView.setProgress(progress);
                    } else {
                        progressView.setProgress(0);
                    }
                }
                invalidate();
            } else {
                MediaController.getInstance().removeLoadingFileObserver(this);
                progressVisible = false;
                if (currentMessageObject.type == 8 && (gifDrawable == null || gifDrawable != null && !gifDrawable.isRunning())) {
                    buttonState = 2;
                } else if (currentMessageObject.type == 3) {
                    buttonState = 3;
                } else {
                    buttonState = -1;
                }
                invalidate();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), photoHeight + AndroidUtilities.dp(14));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        int x;
        if (currentMessageObject.isOut()) {
            x = layoutWidth - backgroundWidth - AndroidUtilities.dp(3);
        } else {
            if (isChat) {
                x = AndroidUtilities.dp(67);
            } else {
                x = AndroidUtilities.dp(15);
            }
        }
        photoImage.setImageCoords(x, AndroidUtilities.dp(7), photoWidth, photoHeight);
        int size = AndroidUtilities.dp(44);
        buttonX = (int)(x + (photoWidth - size) / 2.0f);
        buttonY = (int)(AndroidUtilities.dp(7) + (photoHeight - size) / 2.0f);
        progressView.rect.set(buttonX + AndroidUtilities.dp(2), buttonY + AndroidUtilities.dp(2), buttonX + AndroidUtilities.dp(42), buttonY + AndroidUtilities.dp(42));
    }

    @Override
    protected void onAfterBackgroundDraw(Canvas canvas) {
        if (gifDrawable != null) {
            canvas.save();
            gifDrawable.setBounds(photoImage.getImageX(), photoImage.getImageY(), photoImage.getImageX() + photoWidth, photoImage.getImageY() + photoHeight);
            gifDrawable.draw(canvas);
            canvas.restore();
        } else {
            photoImage.setVisible(!PhotoViewer.getInstance().isShowingImage(currentMessageObject), false);
            photoImage.draw(canvas, photoImage.getImageX(), photoImage.getImageY(), photoWidth, photoHeight);
            drawTime = photoImage.getVisible();
        }

        if (buttonState >= 0 && buttonState < 4) {
            Drawable currentButtonDrawable = buttonStatesDrawables[buttonState];
            setDrawableBounds(currentButtonDrawable, buttonX, buttonY);
            currentButtonDrawable.draw(canvas);
        }

        if (progressVisible) {
            progressView.draw(canvas);
        }

        if (infoLayout != null && (buttonState == 1 || buttonState == 0 || buttonState == 3)) {
            setDrawableBounds(mediaBackgroundDrawable, photoImage.getImageX() + AndroidUtilities.dp(4), photoImage.getImageY() + AndroidUtilities.dp(4), infoWidth + AndroidUtilities.dp(8) + infoOffset, AndroidUtilities.dpf(16.5f));
            mediaBackgroundDrawable.draw(canvas);

            if (currentMessageObject.type == 3) {
                setDrawableBounds(videoIconDrawable, photoImage.getImageX() + AndroidUtilities.dp(8), photoImage.getImageY() + AndroidUtilities.dpf(7.5f));
                videoIconDrawable.draw(canvas);
            }

            canvas.save();
            canvas.translate(photoImage.getImageX() + AndroidUtilities.dp(8) + infoOffset, photoImage.getImageY() + AndroidUtilities.dpf(5.5f));
            infoLayout.draw(canvas);
            canvas.restore();
        }
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState();
    }

    @Override
    public void onSuccessDownload(String fileName) {
        updateButtonState();
        if (currentMessageObject.type == 8 && lastDownloadedGifMessage != null && lastDownloadedGifMessage.messageOwner.id == currentMessageObject.messageOwner.id && buttonState == 2) {
            didPressedButton();
        }
        if (photoNotSet) {
            setMessageObject(currentMessageObject);
        }
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        progressVisible = true;
        progressView.setProgress(progress);
        if (buttonState != 1) {
            updateButtonState();
        }
        invalidate();
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {
        progressView.setProgress(progress);
        invalidate();
    }

    @Override
    public int getObserverTag() {
        return TAG;
    }
}
