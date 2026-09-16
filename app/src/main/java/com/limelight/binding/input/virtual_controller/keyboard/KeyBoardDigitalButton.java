/**
 * Created by Karim Mreisi.
 */

package com.limelight.binding.input.virtual_controller.keyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

import com.limelight.binding.input.virtual_controller.VirtualController;
import com.limelight.binding.input.virtual_controller.VirtualControllerElement;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * This is a digital button on screen element. It is used to get click and double click user input.
 */
public class KeyBoardDigitalButton extends keyBoardVirtualControllerElement {

    /**
     * Listener interface to update registered observers.
     */
    public interface DigitalButtonListener {

        /**
         * onClick event will be fired on button click.
         */
        void onClick();

        /**
         * onLongClick event will be fired on button long click.
         */
        void onLongClick();

        /**
         * onRelease event will be fired on button unpress.
         */
        void onRelease();
    }

    private List<DigitalButtonListener> listeners = new ArrayList<>();
    private String text = "";
    private int icon = -1;
    private long timerLongClickTimeout = 300;
    private final Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            onLongClickCallback();
        }
    };

    private final Paint paint = new Paint();
    private final RectF rect = new RectF();

    private int layer;
    private KeyBoardDigitalButton movingButton = null;
    private boolean sticky = false;
    private boolean clickOnRelease;
    private boolean actionTouchActive;

    boolean inRange(float x, float y) {
        return (this.getX() < x && this.getX() + this.getWidth() > x) &&
                (this.getY() < y && this.getY() + this.getHeight() > y);
    }

    public boolean checkMovement(float x, float y, KeyBoardDigitalButton movingButton) {
        if (clickOnRelease || movingButton.clickOnRelease || enableSwitchDown ||
                movingButton.enableSwitchDown || !enabled || hidden || getVisibility() != VISIBLE) {
            return false;
        }
        // check if the movement happened in the same layer
        if (movingButton.layer != this.layer) {
            return false;
        }

        // save current pressed state
        boolean wasPressed = isPressed();

        // check if the movement directly happened on the button
        if ((this.movingButton == null || movingButton == this.movingButton)
                && this.inRange(x, y)) {
            // set button pressed state depending on moving button pressed state
            if (this.isPressed() != movingButton.isPressed()) {
                this.setPressed(movingButton.isPressed());
            }
        }
        // check if the movement is outside of the range and the movement button
        // is the saved moving button
        else if (movingButton == this.movingButton) {
            this.setPressed(false);
        }

        // check if a change occurred
        if (wasPressed != isPressed()) {
            if (isPressed()) {
                // is pressed set moving button and emit click event
                this.movingButton = movingButton;
                onClickCallback();
            } else {
                // no longer pressed reset moving button and emit release event
                this.movingButton = null;
                onReleaseCallback();
            }

            invalidate();

            return true;
        }

        return false;
    }

    private void checkMovementForAllButtons(float x, float y) {
        for (keyBoardVirtualControllerElement element : virtualController.getElements()) {
            if (element != this && element instanceof KeyBoardDigitalButton) {
                ((KeyBoardDigitalButton) element).checkMovement(x, y, this);
            }
        }
    }

    public KeyBoardDigitalButton(KeyBoardController controller, String elementId, int layer, Context context) {
        super(controller, context, elementId);
        this.layer = layer;
    }

    public void addDigitalButtonListener(DigitalButtonListener listener) {
        listeners.add(listener);
    }

    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    public void setIcon(int id) {
        this.icon = id;
        invalidate();
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
        invalidate();
    }

    public void setClickOnRelease(boolean clickOnRelease) {
        this.clickOnRelease = clickOnRelease;
    }

    public void cancelActionTouch() {
        if (!clickOnRelease) return;
        actionTouchActive = false;
        setPressed(false);
        invalidate();
    }

    private boolean onActionTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                actionTouchActive = true;
                setPressed(true);
                break;
            case MotionEvent.ACTION_MOVE:
                if (event.getX() < 0 || event.getX() >= getWidth() ||
                        event.getY() < 0 || event.getY() >= getHeight()) {
                    cancelActionTouch();
                }
                break;
            case MotionEvent.ACTION_UP:
                boolean activate = actionTouchActive && event.getX() >= 0 &&
                        event.getX() < getWidth() && event.getY() >= 0 && event.getY() < getHeight();
                cancelActionTouch();
                if (activate) {
                    for (DigitalButtonListener listener : listeners) listener.onClick();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                cancelActionTouch();
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    public boolean isSticky() {
        return this.sticky;
    }

    @Override
    protected void onElementDraw(Canvas canvas) {
        // set transparent background
        canvas.drawColor(Color.TRANSPARENT);

        paint.setTextSize(getPercent(getWidth(), 25));
        // Fit longer labels such as "1000000" inside the configurable button width.
        float textWidth = paint.measureText(text);
        if (textWidth > getWidth() * 0.8f) {
            paint.setTextSize(paint.getTextSize() * getWidth() * 0.8f / textWidth);
        }
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeWidth(getDefaultStrokeWidth());

        boolean shouldSetPressed = isPressed() || isSticky();

        paint.setColor(shouldSetPressed ? pressedColor : getDefaultColor());

        paint.setStyle(shouldSetPressed ? Paint.Style.FILL_AND_STROKE: Paint.Style.STROKE);

        rect.left = rect.top = paint.getStrokeWidth();
        rect.right = getWidth() - rect.left;
        rect.bottom = getHeight() - rect.top;

        if(PreferenceConfiguration.readPreferences(getContext()).enableKeyboardSquare){
            canvas.drawRect(rect,paint);
        }else{
            canvas.drawOval(rect, paint);
        }

        if (icon != -1) {
            Drawable d = getResources().getDrawable(icon);
            d.setBounds(5, 5, getWidth() - 5, getHeight() - 5);
            d.draw(canvas);
        } else {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            paint.setStrokeWidth(getDefaultStrokeWidth()/2);
            canvas.drawText(text, getPercent(getWidth(), 50), getPercent(getHeight(), 63), paint);
        }
    }

    private void onClickCallback() {
        _DBG("clicked");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onClick();
        }

        virtualController.getHandler().removeCallbacks(longClickRunnable);
        virtualController.getHandler().postDelayed(longClickRunnable, timerLongClickTimeout);
    }

    private void onLongClickCallback() {
        _DBG("long click");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onLongClick();
        }
    }

    private void onReleaseCallback() {
        _DBG("released");
        // notify listeners
        for (DigitalButtonListener listener : listeners) {
            listener.onRelease();
        }

        // We may be called for a release without a prior click
        virtualController.getHandler().removeCallbacks(longClickRunnable);
    }

    private boolean switchDown;

    private boolean enableSwitchDown;

    public void setEnableSwitchDown(boolean enableSwitchDown) {
        this.enableSwitchDown = enableSwitchDown;
    }

    public void setSwitchDown(boolean switchDown) {
        this.switchDown = switchDown;
        setPressed(switchDown);
        invalidate();
    }

    @Override
    public boolean onElementTouchEvent(MotionEvent event) {
        if (clickOnRelease) return onActionTouchEvent(event);
        // get masked (not specific to a pointer) action
        float x = getX() + event.getX();
        float y = getY() + event.getY();
        int action = event.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                movingButton = null;
                setPressed(true);
                if(enableSwitchDown){
                    switchDown=!switchDown;
                }
                onClickCallback();

                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                // Toggle buttons remain logically pressed after the finger is lifted. Do not
                // allow their touch gesture to slide onto another key, otherwise that key can
                // receive ACTION_DOWN without a matching ACTION_UP when we keep the toggle
                // latched below.
                if (!enableSwitchDown) {
                    checkMovementForAllButtons(x, y);
                }

                return true;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP: {
                if(enableSwitchDown&&switchDown){
                    // The latched branch intentionally skips onReleaseCallback(), but the
                    // pending long-click callback must still be cancelled when touch ends.
                    virtualController.getHandler().removeCallbacks(longClickRunnable);
                    return true;
                }
                setPressed(false);
                onReleaseCallback();

                checkMovementForAllButtons(x, y);

                invalidate();

                return true;
            }
            default: {
            }
        }
        return true;
    }
}
