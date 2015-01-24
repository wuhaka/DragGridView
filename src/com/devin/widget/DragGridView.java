package com.devin.widget;



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.util.Property;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.GridView;

/**
 * A dragable gridview which has two ways for dragging. 
 * And you can srcoll up or down when dragging.
 * It provides drift, swap & shift and springback anmimation.
 * You should implement changing data for an agile adapter.
 * 
 *  See {@link OnSwapListener}
 *  See {@link OnShiftListener}
 *  
 * @author Devin Wong
 *
 */
public class DragGridView extends GridView {
	
	private static final int FINGER_OFFSET = 30;
	private static final int DRIFT_ALPHA = 255 * 6 / 10;
	
	private static final long SHIFT_DELAY = 200L;
	private static final long LONG_CLICK_DURATION = 500L;
	private static final long SWAP_ANIMATION_DURATION = 1500L;
	private static final long SHIFT_ANIMATION_DURATION = 300L;
	private static final long SPRINGBACK_MIN_DURATION = 700L;
	private static final long SPRINGBACK_MAX_DURATION = 1200L;
	private static final long DRIFT_ANIMATION_DURATION = 300L;
	
	private Point mLastPoint = new Point();
	private Point mTouchDownPoint = new Point();
	
	private Rect mSpringbackRect = new Rect();
	private Rect mCurrentDragRect = new Rect();
	private Rect mDriftDragRect = new Rect();
	private Rect mGetPositionRect = new Rect();
	
	private View mDragView;
	private BitmapDrawable mDragDrawable; 
	
	private int mDragPosition;
	private int mMovingPosition;
	private int mLastMovingPosition;
	private int mTouchSlop;
	
	private int mUpScrollBorder;
	private int mDownScrollBorder;
	
	private boolean mSpringbacking; 
	private boolean mDragging; 
	private boolean mShiftAnimating;
	private boolean mDriftAnimating;
    
	private Set<Integer> mExceptionSet;
	
	private OnSwapListener mOnSwapListener;
	private OnShiftListener mOnShiftListener;
	
	private Interpolator mSwapInterpolator = new DecelerateInterpolator(4);
	private Interpolator mShiftInterpolator = new LinearInterpolator();
	private Interpolator mSpringBackInterpolator = new Interpolator() {
		
		@Override
		public float getInterpolation(float input) {
			return (float) (Math.pow(input - 1, 5) + 1);
		}
	};
	
	public DragGridView(Context context) {
		super(context);
		mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
	}
	
	public DragGridView(Context context, AttributeSet attrs) {
		super(context, attrs);
		mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
	}

	public DragGridView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
	}
	
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		if (mSpringbacking) {
			return super.dispatchTouchEvent(ev);
		}
		int x = (int) ev.getX();
		int y = (int) ev.getY();
		int position = getPosition(x, y);
		switch (ev.getAction()) {
		case MotionEvent.ACTION_DOWN:

			mTouchDownPoint.x = x;
			mTouchDownPoint.y = y;
			mUpScrollBorder = getHeight() / 5;
			mDownScrollBorder = getHeight() * 4 / 5;
			mDragPosition = position;
			mLastMovingPosition = position;
			if (mExceptionSet != null && mExceptionSet.contains(mDragPosition)) {
				mDragPosition = AdapterView.INVALID_POSITION;
			}
			if (position == INVALID_POSITION) {
				return super.dispatchTouchEvent(ev);
			}
			View dragView = getChildAt(position - getFirstVisiblePosition());
			mDragView = dragView;
			postDelayed(mLongClickRunnable, LONG_CLICK_DURATION);
			break;
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			if (mDragPosition == INVALID_POSITION) {
				return super.dispatchTouchEvent(ev);
			}
			removeCallbacks(mLongClickRunnable);
			if (mDragging) {
				runSwap();
				springbackToProperPosition();
				mDragging = false;
				return true;
			}
			break;
		case MotionEvent.ACTION_MOVE:
			
			int deltaX = x - mLastPoint.x;
			int deltaY = y - mLastPoint.y;
			if (!mDragging) {
				if (Math.abs(deltaX) > mTouchSlop || Math.abs(deltaY) > mTouchSlop) {
					removeCallbacks(mLongClickRunnable);
				}
				return super.dispatchTouchEvent(ev);
			}
			if (mDragPosition == INVALID_POSITION  || mDriftAnimating) {
				return true;
			}
			mMovingPosition = position;
			View view = getChildAt(position - getFirstVisiblePosition());
			if (mOnShiftListener != null && !mShiftAnimating) {
				if (view != null) {
					setSpringbackRect(view);
				} else {
					setSpringbackRect(getChildAt(getValidCount() - 1));
				}
			}
			mCurrentDragRect.offset(deltaX, deltaY);
			setDrawableRect(mCurrentDragRect);
			if (mLastMovingPosition != position) {
				if (!mShiftAnimating) {
					removeCallbacks(mShiftRunnable);
					postDelayed(mShiftRunnable, SHIFT_DELAY);
					mLastMovingPosition = position;
				}
			}
			
			if (y > mDownScrollBorder) {
				if (getLastVisiblePosition() < getCount() - 1) {
					scroll(false);
				} else if (getLastVisiblePosition() == getCount() - 1) {
					if (getChildAt(getChildCount() - 1).getBottom() - getHeight() > 0) {
						scroll(false);
					}
				}
			} else if (y < mUpScrollBorder) {
				if (getFirstVisiblePosition() > 0) {
					scroll(true);
				} else if (getFirstVisiblePosition() == 0) {
					if (getChildAt(0).getTop() < 0) {
						scroll(true);
					}
				}
			}
			if (mDragging) {
				mLastPoint.set(x, y);
				return true;
			}
		default:
			if (mDragging) {
				return true;
			}
		}
		mLastPoint.set(x, y);
		return super.dispatchTouchEvent(ev);
	}
	
	public void scroll(boolean up) {
		removeCallbacks(mShiftRunnable);
		if (up) {
			smoothScrollBy(-50, 0);
		} else {
			smoothScrollBy(50, 0);
		}
		postDelayed(mShiftRunnable, SHIFT_DELAY);
		
	}
	
    @Override
	public boolean onTouchEvent(MotionEvent ev) {
		return super.onTouchEvent(ev);
	}

	public int getPosition(int x, int y) {
		Rect frame = mGetPositionRect;
        final int count = getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = getChildAt(i);
            child.getHitRect(frame);
            if (frame.contains(x, y)) {
            	if (i >= getValidCount()) {
            		return INVALID_POSITION;
            	} else {
            		return getFirstVisiblePosition() + i;
            	}
            }
        }
        return INVALID_POSITION;
    }
	
	class DrawableProxy {
		
		public static final String LEFT = "left";
		public static final String TOP = "top";
		public static final String RIGHT = "right";
		public static final String BOTTOM = "bottom";
		
		private int left;
		private int top;
		private int right;
		private int bottom;
		
		public int getLeft() {
			return left;
		}
		public void setLeft(int left) {
			this.left = left;
		}
		public int getTop() {
			return top;
		}
		public void setTop(int top) {
			this.top = top;
		}
		public int getRight() {
			return right;
		}
		public void setRight(int right) {
			this.right = right;
		}
		public int getBottom() {
			return bottom;
		}
		public void setBottom(int bottom) {
			this.bottom = bottom;
		}
		
		Property<DrawableProxy, Integer> leftProperty = new IntegerProperty<DrawableProxy>(DrawableProxy.LEFT) {

			@Override
			public void setValue(DrawableProxy object, int value) {
				object.setLeft(value);
			}

			@Override
			public Integer get(DrawableProxy object) {
				return object.getLeft();
			}
			
		};
		Property<DrawableProxy, Integer> topProperty = new IntegerProperty<DrawableProxy>(DrawableProxy.TOP) {

			@Override
			public void setValue(DrawableProxy object, int value) {
				object.setTop(value);
			}

			@Override
			public Integer get(DrawableProxy object) {
				return object.getTop();
			}
			
		};
		
		Property<DrawableProxy, Integer> rightProperty = new IntegerProperty<DrawableProxy>(DrawableProxy.RIGHT) {

			@Override
			public void setValue(DrawableProxy object, int value) {
				object.setRight(value);
			}

			@Override
			public Integer get(DrawableProxy object) {
				return object.getRight();
			}
			
		};
		
		Property<DrawableProxy, Integer> bottomProperty = new IntegerProperty<DrawableProxy>(DrawableProxy.BOTTOM) {

			@Override
			public void setValue(DrawableProxy object, int value) {
				object.setBottom(value);
			}

			@Override
			public Integer get(DrawableProxy object) {
				return object.getBottom();
			}
			
		};

		//framework的IntProperty被@hide, Copy it
		public abstract class IntegerProperty<T> extends Property<T, Integer> {

		    public IntegerProperty(String name) {
		        super(Integer.class, name);
		    }

		    public abstract void setValue(T object, int value);

		    @Override
		    final public void set(T object, Integer value) {
		        setValue(object, value.intValue());
		    }
		}
	}
	
	public ObjectAnimator createDrawableProxyAnimator(DrawableProxy proxy, Rect origin, Rect des) {
		PropertyValuesHolder leftHolder = PropertyValuesHolder.ofInt(proxy.leftProperty, origin.left, des.left);
		PropertyValuesHolder topHolder = PropertyValuesHolder.ofInt(proxy.topProperty, origin.top, des.top);
		PropertyValuesHolder rightHolder = PropertyValuesHolder.ofInt(proxy.rightProperty, origin.right, des.right);
		PropertyValuesHolder bottomHolder = PropertyValuesHolder.ofInt(proxy.bottomProperty, origin.bottom, des.bottom);
		ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(proxy, leftHolder, topHolder, rightHolder, bottomHolder);
		return animator;
	}
	
	private void sticktoFinger() {
		if (mDragDrawable != null) {
			final DrawableProxy proxy = new DrawableProxy();
			ObjectAnimator animator = createDrawableProxyAnimator(proxy, mDriftDragRect, mCurrentDragRect);
			animator.addUpdateListener(new AnimatorUpdateListener() {
				
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					mDriftDragRect.set(proxy.left, proxy.top, proxy.right, proxy.bottom);
					setDrawableRect(mDriftDragRect);
				}
			});
			animator.addListener(new AnimatorListener() {
				
				@Override
				public void onAnimationStart(Animator animation) {
					mDriftAnimating = true;
				}
				
				@Override
				public void onAnimationRepeat(Animator animation) {
					mDriftAnimating = true;
					
				}
				
				@Override
				public void onAnimationEnd(Animator animation) {
					mDriftAnimating = false;
					
				}
				
				@Override
				public void onAnimationCancel(Animator animation) {
					mDriftAnimating = false;
				}
			});
			animator.setDuration(DRIFT_ANIMATION_DURATION);
			animator.start();
		}
	}

	private void springbackToProperPosition() {
		if (mDragDrawable != null) {
			final DrawableProxy proxy = new DrawableProxy();
			ObjectAnimator animator = createDrawableProxyAnimator(proxy, mCurrentDragRect, mSpringbackRect);
			animator.addUpdateListener(new AnimatorUpdateListener() {
				
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					mCurrentDragRect.set(proxy.left, proxy.top, proxy.right, proxy.bottom);
					setDrawableRect(mCurrentDragRect);
				}
			});
			animator.addListener(new AnimatorListener() {
				
				@Override
				public void onAnimationStart(Animator animation) {
					mSpringbacking = true;
				}
				
				@Override
				public void onAnimationRepeat(Animator animation) {
					mSpringbacking = true;
				}
				
				@Override
				public void onAnimationEnd(Animator animation) {
					mSpringbacking = false;
					mDragDrawable = null;
					for (int i = 0; i < getValidCount(); i++) {
						getChildAt(i).setVisibility(View.VISIBLE);
					}
				}
				
				@Override
				public void onAnimationCancel(Animator animation) {
					mSpringbacking = false;
				}
			});
			animator.setInterpolator(mSpringBackInterpolator);
			float deltaX = Math.abs(mSpringbackRect.left - mCurrentDragRect.left);
			float deltaY = Math.abs(mSpringbackRect.top - mCurrentDragRect.top);
			long duration = (long) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
			duration = clamp(duration * 3, SPRINGBACK_MIN_DURATION, SPRINGBACK_MAX_DURATION);
			animator.setInterpolator(mSpringBackInterpolator);
			animator.setDuration(duration);
			animator.start();
		}
	}
	
	public static long clamp(long x, long min, long max) {
        return x > max ? max : (x < min ?  min : x);
    }
	
	private static class ViewPositionInfo {
		public View view;
		public float x;
		public float y;
	}
	   
		
	private Map<Long, ViewPositionInfo> collectViewPositionInfo() {
		int childCount = getValidCount();
		if (childCount > 0) {
			Map<Long, ViewPositionInfo> info = new HashMap<Long, ViewPositionInfo>();
			int offset = getFirstVisiblePosition();
			for (int i = 0; i < childCount; ++i) {
				ViewPositionInfo inf = new ViewPositionInfo();
				View child = getChildAt(i);
				inf.view = child;
				inf.x = child.getX();
				inf.y = child.getY();
				info.put(getAdapter().getItemId(offset + i), inf);
			}
			return info;
		}
		return null;
	}
	
	private void startAnimationForShift(Map<Long, ViewPositionInfo> oldInfo, Map<Long, ViewPositionInfo> newInfo) {
		List<Animator> animators = new ArrayList<Animator>();
		if (newInfo != null) {
			for (Long id : newInfo.keySet()) {
				ViewPositionInfo oldInf = oldInfo.get(id);
				ViewPositionInfo newInf = newInfo.get(id);
				float xDiff = 0;
				float yDiff = 0;
				if (oldInf == null) {
				} else {
					xDiff = oldInf.x - newInf.x;
					yDiff = oldInf.y - newInf.y;
				}
				if (xDiff != 0) {
					animators.add(ObjectAnimator.ofFloat(newInf.view, TRANSLATION_X, xDiff, 0));
				}
				if (yDiff != 0) {
					animators.add(ObjectAnimator.ofFloat(newInf.view, TRANSLATION_Y, yDiff, 0));
				}
			}
		}
		if (animators.size() > 0) {
			AnimatorSet set = new AnimatorSet();
			set.addListener(new AnimatorListener() {
				
				@Override
				public void onAnimationStart(Animator animation) {
					mShiftAnimating = true;
					mDragView.setVisibility(View.VISIBLE);
					View dragView = null;
					if (mMovingPosition == INVALID_POSITION) {
						dragView = getChildAt(getValidCount() - 1);
						mDragPosition = getValidCount() - 1;
					} else {
						dragView = getChildAt(mMovingPosition - getFirstVisiblePosition());
						mDragPosition = mMovingPosition;
					}
					dragView.setVisibility(View.INVISIBLE);
					mDragView = dragView;
					setSpringbackRect(dragView);
				}
				
				@Override
				public void onAnimationRepeat(Animator animation) {
					mShiftAnimating = true;
				}
				
				@Override
				public void onAnimationEnd(Animator animation) {
					mShiftAnimating = false;
				}
				
				@Override
				public void onAnimationCancel(Animator animation) {
					mShiftAnimating = false;
				}
			});
			set.playTogether(animators);
			set.setInterpolator(mShiftInterpolator);
			set.setDuration(SHIFT_ANIMATION_DURATION);
			set.start();
		}
	}
	
	private void startAnimationForSwap(int start, int end) {
		final View startView = getChildAt(start - getFirstVisiblePosition());
		final View endView = getChildAt(end - getFirstVisiblePosition());
		AnimatorSet set = new AnimatorSet();
		set.addListener(new AnimatorListener() {
			
			@Override
			public void onAnimationStart(Animator animation) {
				startView.setVisibility(View.VISIBLE);
				endView.setVisibility(View.INVISIBLE);
				setSpringbackRect(endView);
			}
			
			@Override
			public void onAnimationRepeat(Animator animation) {
				
			}
			
			@Override
			public void onAnimationEnd(Animator animation) {
			}
			
			@Override
			public void onAnimationCancel(Animator animation) {
				
			}
		});
		List<Animator> animators = new ArrayList<Animator>();
		animators.add(ObjectAnimator.ofFloat(startView, X, endView.getX(), startView.getX()));
		animators.add(ObjectAnimator.ofFloat(startView, Y, endView.getY(), startView.getY()));
		set.playTogether(animators);
		set.setInterpolator(mSwapInterpolator);
		set.setDuration(SWAP_ANIMATION_DURATION);
		set.start();
	}

	private Runnable mSwapRunnable = new Runnable() {

		@Override
		public void run() {
			runSwap();
		}

	};
	
	private void runSwap() {
		if (mOnSwapListener != null) {
			if (mExceptionSet != null && mExceptionSet.contains(mMovingPosition)) {
				mMovingPosition = AdapterView.INVALID_POSITION;
			}
			if (mMovingPosition == INVALID_POSITION) {
				return;
			}
			mOnSwapListener.onSwap(mDragPosition, mMovingPosition);
			startAnimationForSwap(mDragPosition, mMovingPosition);
		}
	}
	
	private Runnable mShiftRunnable = new Runnable() {

		@Override
		public void run() {
			if (mOnShiftListener != null) {
				Map<Long, ViewPositionInfo> beforeSwapMap = collectViewPositionInfo();
				mOnShiftListener.onShift(mDragPosition, mMovingPosition);
				Map<Long, ViewPositionInfo> afterSwapMap = collectViewPositionInfo();
				startAnimationForShift(beforeSwapMap, afterSwapMap);
			}
		}
	};
	
	
	private Runnable mLongClickRunnable = new Runnable() {

		@Override
		public void run() {
			View dragView = mDragView;
			if (dragView != null) {
				dragView.setVisibility(View.INVISIBLE);
				dragView.setDrawingCacheEnabled(true);
				Bitmap bitmap = Bitmap.createBitmap(dragView.getDrawingCache());
				mDragDrawable = new BitmapDrawable(getResources(), bitmap);
				dragView.destroyDrawingCache();
				setSpringbackRect(dragView);
				mCurrentDragRect.set(mSpringbackRect);
				mDriftDragRect.set(mSpringbackRect);
				setDrawableRect(mCurrentDragRect);
				int deltaX = mTouchDownPoint.x - mSpringbackRect.centerX();
				int deltaY = mTouchDownPoint.y - mSpringbackRect.centerY();
				mCurrentDragRect.offset(deltaX - FINGER_OFFSET, deltaY - FINGER_OFFSET);
				sticktoFinger();
				mDragging = true;
			}
		}
	};
	
	
	
	private void setDrawableRect(Rect rect) {
		if (mDragDrawable != null) {
			Rect r = mDragDrawable.getBounds();
			invalidate(r);
			mDragDrawable.setBounds(rect);
			invalidate(rect);
		}
	}
	
	public void setSpringbackRect(View view) {
		mSpringbackRect.set(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
	}


	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		if (mDragDrawable != null) {
			mDragDrawable.setAlpha(DRIFT_ALPHA);
			mDragDrawable.draw(canvas);
		}
	}

	public void setExceptionPosition(Set<Integer> set) {
		mExceptionSet = set;
	}
	

	public interface OnSwapListener {
		/**
		 * onSwap example：
		 * 	List list = adapter.getList();
			Object temp = list.get(dragPos);
			list.set(dragPos, list.get(movePos));
			list.set(movePos, temp);
		 *
		 */
		public void onSwap(int dragPos, int movePos);
	}
	
	/**
	 * You should return single itemId();
	 */
	public interface OnShiftListener {
		/**
		 * OnShift example：
		 * 	List list = adapter.getList();
			if (movePos == -1) {
				Object drag = list.get(dragPos);
				for (int i = dragPos; i < list.size() - 1; i++) {
					list.set(i, list.get(i + 1));
				}
				list.set(list.size() - 1, drag);	
			} else if (dragPos < movePos) {
				Object drag = list.get(dragPos);
				for (int i = dragPos; i < movePos; i++) {
					list.set(i, list.get(i + 1));
				}
				list.set(movePos, drag);
			} else {
				Object drag = list.get(dragPos);
				for (int i = dragPos; i > movePos; i--) {
					list.set(i, list.get(i - 1));
				}
				list.set(movePos, drag);
			}
		 *
		 */
		public void onShift(int dragPos, int movePos);
	}
	
	public void setOnSwapListener(OnSwapListener listener) {
		if (mOnShiftListener != null) {
			throw new IllegalStateException("DragGridView has registered OnShiftListener, please set either");
		}
		mOnSwapListener = listener;
	}
	
	public void setOnShiftListener(OnShiftListener listener) {
		if (mOnSwapListener != null) {
			throw new IllegalStateException("DragGridView has registered OnSwapListener, please set either");
		}
		mOnShiftListener = listener;
	}
	
	public int getValidCount() {
		return getChildCount();
	}
	


	@Override
	public void onWindowFocusChanged(boolean hasWindowFocus) {
		super.onWindowFocusChanged(hasWindowFocus);
		if (!hasWindowFocus) {
			  final MotionEvent event = MotionEvent.obtain(0, 0,
		                MotionEvent.ACTION_CANCEL, 0, 0, 0);
		        dispatchTouchEvent(event);
		}
	}
	
	
	
}