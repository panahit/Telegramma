/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Components;

import android.content.Context;
import org.telegram.android.support.widget.RecyclerView;

import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.FileLog;

public class RecyclerListView extends RecyclerView {

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public static class RecyclerListViewItemClickListener implements RecyclerView.OnItemTouchListener {
        private OnItemClickListener mListener;

        GestureDetector mGestureDetector;

        public RecyclerListViewItemClickListener(Context context, OnItemClickListener listener) {
            mListener = listener;
            mGestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    return true;
                }
            });
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent e) {
            View childView = view.findChildViewUnder(e.getX(), e.getY());
            if (childView != null) {
                if (mListener != null && mGestureDetector.onTouchEvent(e)) {
                    mListener.onItemClick(childView, view.getChildPosition(childView));
                }
                /*int action = e.getAction();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                    childView.setPressed(true);
                } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                    childView.setPressed(false);
                }*/
            } else {
                /*int count = view.getChildCount();
                for (int a = 0; a < count; a++) {
                    view.getChildAt(a).setPressed(false);
                }*/
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView view, MotionEvent e) {

        }
    }

    public RecyclerListView(Context context) {
        super(context);
    }

    public RecyclerListView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RecyclerListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        requestDisallowInterceptTouchEvent(true);
        return super.onInterceptTouchEvent(e);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        try {
            super.onLayout(changed, l, t, r, b);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    @Override
    public void stopScroll() {
        try {
            super.stopScroll();
        } catch (NullPointerException exception) {
            /**
             *  The mLayout has been disposed of before the
             *  RecyclerView and this stops the application
             *  from crashing.
             */
        }
    }
}
