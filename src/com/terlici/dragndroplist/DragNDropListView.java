/*
 * Copyright 2012 Terlici Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.terlici.dragndroplist;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Adapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.WrapperListAdapter;

public class DragNDropListView extends ListView {
	
	public static interface OnItemDragNDropListener {
		public void onItemDrag(DragNDropListView parent, View view, int position, long id);
        public void onItemDragMoves(DragNDropListView parent, int startPosition, int endPosition);
		public void onItemDrop(DragNDropListView parent, View view, int startPosition, int endPosition, long id);
	}
	
	boolean mDragMode;

	WindowManager mWm;
	int mStartPosition = INVALID_POSITION;
	int mDragPointOffset; // Used to adjust drag view location
	int mDragHandler = 0;
	
	ImageView mDragView;
    View mItemView;
	
	OnItemDragNDropListener mDragNDropListener;

	private void init() {
		mWm = (WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
	}
	
	public DragNDropListView(Context context) {
		super(context);

		init();
	}
	
	public DragNDropListView(Context context, AttributeSet attrs) {
		super(context, attrs);

		init();
	}
	
	public DragNDropListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		init();
	}
	
	public void setOnItemDragNDropListener(OnItemDragNDropListener listener) {
		mDragNDropListener = listener;
	}
	
	public void setDragNDropAdapter(DragNDropAdapter adapter) {
		mDragHandler = adapter.getDragHandler();
		setAdapter(adapter);
	}
	
	/**
	 * If the motion event was inside a handler view.
	 *  
	 * @param ev
	 * @return true if it is a dragging move, false otherwise.
	 */
	public boolean isDrag(MotionEvent ev) {
		if (mDragMode) return true;
		if (mDragHandler == 0) return false;
		
		int x = (int)ev.getX();
		int y = (int)ev.getY();
		
		int startposition = pointToPosition(x,y);
		
		if (startposition == INVALID_POSITION) return false;
		
		int childposition = startposition - getFirstVisiblePosition();
		View parent = getChildAt(childposition);
		View handler = parent.findViewById(mDragHandler);
		
		if (handler == null) return false;
		
		int top = parent.getTop() + handler.getTop();
		int bottom = top + handler.getHeight();
		int left = parent.getLeft() + handler.getLeft();
		int right = left + handler.getWidth();
		
		return left <= x && x <= right && top <= y && y <= bottom;
	}
	
	public boolean isDragging() {
		return mDragMode;
	}
	
	public View getDragView() {
		return mDragView;
	}

    public void smoothScrollByOffset(int position) {
        int index = -1;
        if (position < 0) {
            index = getFirstVisiblePosition();
        } else if (position > 0) {
            index = getLastVisiblePosition();
        }

        if (index > -1) {
            View child = getChildAt(index - getFirstVisiblePosition());
            if (child != null) {
                Rect visibleRect = new Rect();
                if (child.getGlobalVisibleRect(visibleRect)) {
                    // the child is partially visible
                    int childRectArea = child.getWidth() * child.getHeight();
                    int visibleRectArea = visibleRect.width() * visibleRect.height();
                    float visibleArea = (visibleRectArea / (float) childRectArea);
                    final float visibleThreshold = 0.75f;
                    if ((position < 0) && (visibleArea < visibleThreshold)) {
                        // the top index is not perceivably visible so offset
                        // to account for showing that top index as well
                        ++index;
                    } else if ((position > 0) && (visibleArea < visibleThreshold)) {
                        // the bottom index is not perceivably visible so offset
                        // to account for showing that bottom index as well
                        --index;
                    }
                }
                smoothScrollToPosition(Math.max(0, Math.min(getCount(), index + position)));
            }
        }
    }
	
	@Override
	public boolean onTouchEvent(MotionEvent ev) {
		final int action = ev.getAction();
		final int x = (int)ev.getX();
		final int y = (int)ev.getY();
		
		
		if (action == MotionEvent.ACTION_DOWN && isDrag(ev)) mDragMode = true;

		if (!mDragMode) return super.onTouchEvent(ev);

		switch (action) {
			case MotionEvent.ACTION_DOWN:
				mStartPosition = pointToPosition(x, y);
				
				if (mStartPosition != INVALID_POSITION) {
					int childPosition = mStartPosition - getFirstVisiblePosition();
                    mDragPointOffset = y - getChildAt(childPosition).getTop();
                    mDragPointOffset -= ((int)ev.getRawY()) - y;
                    
					startDrag(childPosition, y);
					drag(0, y);
				}
				
				break;
			case MotionEvent.ACTION_MOVE:
				drag(0, y);
                if (mStartPosition != INVALID_POSITION) {
                    int actualPosition =  pointToPosition(x,y);
                    onDragMoves(actualPosition);

                    if(y <= getTop())
                    {
                        smoothScrollByOffset(- 1);
                    }
                    else if(y + mDragView.getMeasuredHeight() >= getTop() + getMeasuredHeight() )
                    {
                        smoothScrollByOffset(1);
                    }
                }
				break;
			case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
			default:
				mDragMode = false;
				

				if (mStartPosition != INVALID_POSITION) {
					// check if the position is a header/footer
					int actualPosition =  pointToPosition(x,y);
					if (actualPosition > (getCount() - getFooterViewsCount()) - 1)
						actualPosition = INVALID_POSITION;

					stopDrag(mStartPosition - getFirstVisiblePosition(), actualPosition);
				}
				break;
		}
		
		return true;
	}
	
	/**
	 * Prepare the drag view.
	 * 
	 * @param childIndex
	 * @param y
	 */
	private void startDrag(int childIndex, int y) {
        mItemView = getChildAt(childIndex);
		
		if (mItemView == null) return;

		long id = getItemIdAtPosition(mStartPosition);

		if (mDragNDropListener != null)
        	mDragNDropListener.onItemDrag(this, mItemView, mStartPosition, id);

        Adapter adapter = getAdapter();
        DragNDropAdapter dndAdapter;

        // if exists a footer/header we have our adapter wrapped
        if (adapter instanceof WrapperListAdapter) {
            dndAdapter = (DragNDropAdapter)((WrapperListAdapter)adapter).getWrappedAdapter();
        } else {
            dndAdapter = (DragNDropAdapter)adapter;
        }

        dndAdapter.onItemDrag(this, mItemView, mStartPosition, id);

        mItemView.setDrawingCacheEnabled(true);
		
        // Create a copy of the drawing cache so that it does not get recycled
        // by the framework when the list tries to clean up memory
        Bitmap bitmap = Bitmap.createBitmap(mItemView.getDrawingCache());
        
        WindowManager.LayoutParams mWindowParams = new WindowManager.LayoutParams();
        mWindowParams.gravity = Gravity.TOP;
        mWindowParams.x = 0;
        mWindowParams.y = y - mDragPointOffset;

        mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        mWindowParams.format = PixelFormat.TRANSLUCENT;
        mWindowParams.windowAnimations = 0;
        
        Context context = getContext();
        ImageView v = new ImageView(context);
        v.setImageBitmap(bitmap);

        mWm.addView(v, mWindowParams);
        mDragView = v;

        mItemView.setVisibility(View.INVISIBLE);
        mItemView.invalidate(); // We have not changed anything else.
	}

    private void onDragMoves(int endPosition)
    {
        Adapter adapter = getAdapter();
        DragNDropAdapter dndAdapter;

        // if exists a footer/header we have our adapter wrapped
        if (adapter instanceof WrapperListAdapter) {
            dndAdapter = (DragNDropAdapter)((WrapperListAdapter)adapter).getWrappedAdapter();
        } else {
            dndAdapter = (DragNDropAdapter)adapter;
        }

        dndAdapter.onItemDragMoves(this, mStartPosition, endPosition);
    }
	
	/**
	 * Release all dragging resources.
	 * 
	 * @param childIndex
	 */
	private void stopDrag(int childIndex, int endPosition) {
		if (mDragView == null) return;

//		View item = getChildAt(childIndex);

        if (endPosition != INVALID_POSITION) {
            long id = getItemIdAtPosition(mStartPosition);

            if (mDragNDropListener != null)
                mDragNDropListener.onItemDrop(this, mItemView, mStartPosition, endPosition, id);

            Adapter adapter = getAdapter();
            DragNDropAdapter dndAdapter;

            // if exists a footer/header we have our adapter wrapped
            if (adapter instanceof WrapperListAdapter) {
                dndAdapter = (DragNDropAdapter)((WrapperListAdapter)adapter).getWrappedAdapter();
            } else {
                dndAdapter = (DragNDropAdapter)adapter;
            }

            dndAdapter.onItemDrop(this, mItemView, mStartPosition, endPosition, id);
        }
		
        mDragView.setVisibility(GONE);
        mWm.removeView(mDragView);
        
        mDragView.setImageDrawable(null);
        mDragView = null;

        mItemView.setDrawingCacheEnabled(false);
        mItemView.destroyDrawingCache();

        mItemView.setVisibility(View.VISIBLE);
        mItemView = null;

        mStartPosition = INVALID_POSITION;
        
        invalidateViews(); // We have changed the adapter data, so change everything
	}
	
	/**
	 * Move the drag view.
	 * 
	 * @param x
	 * @param y
	 */
	private void drag(int x, int y) {
		if (mDragView == null) return;

		WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams)mDragView.getLayoutParams();
		layoutParams.x = x;
		layoutParams.y = y - mDragPointOffset;

		mWm.updateViewLayout(mDragView, layoutParams);
	}
}
